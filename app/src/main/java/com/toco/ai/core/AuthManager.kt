package com.toco.ai.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.toco.ai.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Small Supabase Auth client used by TOCO Beta 1.5.
 *
 * Only the publishable Supabase key is present in the APK. Service-role/admin
 * credentials never belong on the device. Session tokens are encrypted at rest.
 */
class AuthManager(private val context: Context) {

    data class Result(val ok: Boolean, val message: String)

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "toco_secure_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val isSignedIn: Boolean
        get() = !accessToken().isNullOrBlank()

    val accountLabel: String
        get() = securePrefs.getString(KEY_IDENTITY, null)
            ?: securePrefs.getString(KEY_USER_ID, null)
            ?: "Signed in"

    fun accessToken(): String? = securePrefs.getString(KEY_ACCESS, null)

    fun googleIntent(): Intent {
        val redirect = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        val url = "${BuildConfig.SUPABASE_URL}/auth/v1/authorize?provider=google&redirect_to=$redirect"
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    fun acceptOAuthCallback(uri: Uri): Result {
        val values = mutableMapOf<String, String>()
        uri.query?.split("&")?.forEach { putPair(it, values) }
        uri.fragment?.split("&")?.forEach { putPair(it, values) }

        val error = values["error_description"] ?: values["error"]
        if (!error.isNullOrBlank()) return Result(false, Uri.decode(error))

        val access = values["access_token"]
        if (access.isNullOrBlank()) {
            return Result(false, "Google sign-in returned without a session. Check the Google provider and redirect URL in Supabase.")
        }

        val refresh = values["refresh_token"]
        val expires = values["expires_in"]?.toLongOrNull() ?: 3600L
        saveSession(access, refresh, System.currentTimeMillis() + expires * 1000L, null, "Google account")
        return Result(true, "Signed in with Google")
    }

    fun signInTocoId(rawId: String, password: String, callback: (Result) -> Unit) {
        val email = normalizeTocoId(rawId)
        if (email == null) {
            callback(Result(false, "Use 3–32 letters/numbers, dots, _ or - for your TOCO ID."))
            return
        }
        if (password.length < 6) {
            callback(Result(false, "Password must be at least 6 characters."))
            return
        }
        async(callback) {
            authRequest(
                "/auth/v1/token?grant_type=password",
                JSONObject().put("email", email).put("password", password)
            ).let { parseSessionResponse(it, email) }
        }
    }

    fun createTocoId(rawId: String, password: String, callback: (Result) -> Unit) {
        val email = normalizeTocoId(rawId)
        if (email == null) {
            callback(Result(false, "Use 3–32 letters/numbers, dots, _ or - for your TOCO ID."))
            return
        }
        if (password.length < 8) {
            callback(Result(false, "Use at least 8 characters for your password."))
            return
        }
        async(callback) {
            val response = authRequest(
                "/auth/v1/signup",
                JSONObject().put("email", email).put("password", password)
            )
            val parsed = parseSessionResponse(response, email)
            if (parsed.ok) {
                upsertProfile(email)
                parsed
            } else if (response.code in 200..299) {
                Result(false, "TOCO ID created, but this Supabase project currently requires email confirmation. Use Google/phone until @toco.io mail delivery is enabled.")
            } else parsed
        }
    }

    fun sendPhoneCode(phone: String, callback: (Result) -> Unit) {
        val clean = phone.trim()
        if (!clean.startsWith("+") || clean.length < 8) {
            callback(Result(false, "Enter your full phone number with country code, e.g. +880…"))
            return
        }
        async(callback) {
            val response = authRequest("/auth/v1/otp", JSONObject().put("phone", clean))
            if (response.code in 200..299) Result(true, "Code sent")
            else Result(false, response.errorMessage("Could not send verification code."))
        }
    }

    fun verifyPhoneCode(phone: String, code: String, callback: (Result) -> Unit) {
        val clean = phone.trim()
        if (code.trim().length < 4) {
            callback(Result(false, "Enter the verification code."))
            return
        }
        async(callback) {
            val response = authRequest(
                "/auth/v1/verify",
                JSONObject().put("type", "sms").put("phone", clean).put("token", code.trim())
            )
            parseSessionResponse(response, clean)
        }
    }

    fun signOut(callback: ((Result) -> Unit)? = null) {
        val token = accessToken()
        if (token.isNullOrBlank()) {
            clearSession()
            callback?.invoke(Result(true, "Signed out"))
            return
        }
        io.execute {
            try {
                request("/auth/v1/logout", "POST", "{}", token)
            } catch (_: Exception) {
                // Local sign-out must still succeed even if the network is down.
            }
            clearSession()
            main.post { callback?.invoke(Result(true, "Signed out")) }
        }
    }

    private fun upsertProfile(tocoId: String) {
        val token = accessToken() ?: return
        val userId = securePrefs.getString(KEY_USER_ID, null) ?: return
        val body = JSONObject()
            .put("user_id", userId)
            .put("toco_id", tocoId)
            .toString()
        request(
            "/rest/v1/toco_profiles?on_conflict=user_id",
            "POST",
            body,
            token,
            mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        )
    }

    private fun parseSessionResponse(response: HttpResult, identity: String): Result {
        if (response.code !in 200..299) {
            return Result(false, response.errorMessage("Sign-in failed."))
        }
        val json = try { JSONObject(response.body) } catch (_: Exception) { JSONObject() }
        val access = json.optString("access_token")
        if (access.isBlank()) {
            return Result(false, "Account exists, but no login session was issued.")
        }
        val refresh = json.optString("refresh_token").takeIf { it.isNotBlank() }
        val expires = json.optLong("expires_in", 3600L)
        val user = json.optJSONObject("user")
        val userId = user?.optString("id")?.takeIf { !it.isNullOrBlank() }
        saveSession(access, refresh, System.currentTimeMillis() + expires * 1000L, userId, identity)
        return Result(true, "Signed in")
    }

    private fun saveSession(
        access: String,
        refresh: String?,
        expiresAt: Long,
        userId: String?,
        identity: String?
    ) {
        securePrefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putLong(KEY_EXPIRES, expiresAt)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_IDENTITY, identity)
            .apply()
    }

    private fun clearSession() {
        securePrefs.edit().clear().apply()
    }

    private fun authRequest(path: String, body: JSONObject): HttpResult =
        request(path, "POST", body.toString(), null)

    private fun request(
        path: String,
        method: String,
        body: String?,
        bearer: String?,
        extraHeaders: Map<String, String> = emptyMap()
    ): HttpResult {
        val connection = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) doOutput = true
        }
        if (body != null) {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { input -> BufferedReader(InputStreamReader(input)).readText() }.orEmpty()
        connection.disconnect()
        return HttpResult(code, text)
    }

    private fun async(callback: (Result) -> Unit, block: () -> Result) {
        io.execute {
            val result = try {
                block()
            } catch (_: Exception) {
                Result(false, "Could not reach TOCO securely. Check your connection and try again.")
            }
            main.post { callback(result) }
        }
    }

    private fun normalizeTocoId(raw: String): String? {
        val local = raw.trim().lowercase().removeSuffix("@toco.io")
        if (!local.matches(Regex("^[a-z0-9._-]{3,32}$"))) return null
        return "$local@toco.io"
    }

    private fun putPair(pair: String, target: MutableMap<String, String>) {
        val split = pair.split("=", limit = 2)
        if (split.isEmpty()) return
        target[Uri.decode(split[0])] = Uri.decode(split.getOrElse(1) { "" })
    }

    private data class HttpResult(val code: Int, val body: String) {
        fun errorMessage(fallback: String): String {
            return try {
                val json = JSONObject(body)
                json.optString("msg")
                    .ifBlank { json.optString("message") }
                    .ifBlank { json.optString("error_description") }
                    .ifBlank { fallback }
            } catch (_: Exception) {
                fallback
            }
        }
    }

    private companion object {
        const val REDIRECT_URI = "toco://auth/callback"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_USER_ID = "user_id"
        const val KEY_IDENTITY = "identity"
    }
}

package com.toco.ai.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.toco.ai.R
import com.toco.ai.core.AuthManager
import com.toco.ai.core.Prefs
import com.toco.ai.ui.MainActivity
import com.toco.ai.ui.onboarding.OnboardingActivity

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: AuthManager
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var phoneCode: EditText
    private lateinit var verifyPhone: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = AuthManager(this)
        if (intent?.data?.scheme == "toco") {
            val result = auth.acceptOAuthCallback(intent.data!!)
            if (result.ok) { goNext(); return }
        }
        if (auth.isSignedIn) { goNext(); return }
        setContentView(R.layout.activity_login)

        status = findViewById(R.id.loginStatus)
        progress = findViewById(R.id.loginProgress)
        phoneCode = findViewById(R.id.phoneCode)
        verifyPhone = findViewById(R.id.verifyPhone)

        val title = findViewById<TextView>(R.id.authTitle)
        val subtitle = findViewById<TextView>(R.id.authSubtitle)
        val idPanel = findViewById<View>(R.id.idPanel)
        val createPanel = findViewById<View>(R.id.createPanel)
        val phonePanel = findViewById<View>(R.id.phonePanel)
        val socialPanel = findViewById<View>(R.id.socialPanel)
        val createLink = findViewById<View>(R.id.createAccountLink)
        val back = findViewById<View>(R.id.backToLogin)

        fun mode(name: String) {
            idPanel.visibility = if (name == "login") View.VISIBLE else View.GONE
            createPanel.visibility = if (name == "create") View.VISIBLE else View.GONE
            phonePanel.visibility = if (name == "phone") View.VISIBLE else View.GONE
            createLink.visibility = if (name == "login") View.VISIBLE else View.GONE
            back.visibility = if (name == "login") View.GONE else View.VISIBLE
            when (name) {
                "create" -> { title.text = "Create your account"; subtitle.text = "Choose your TOCO identity. Your private agent starts here." }
                "phone" -> { title.text = "Phone login"; subtitle.text = "We'll send a verification code to your number." }
                else -> { title.text = "Welcome back"; subtitle.text = "Your AI agent, ready when you are." }
            }
            status.text = "Private by design. Secured with your account."
        }

        findViewById<View>(R.id.googleLogin).setOnClickListener { startActivity(auth.googleIntent()) }
        findViewById<View>(R.id.phoneLogin).setOnClickListener { mode("phone") }
        createLink.setOnClickListener { mode("create") }
        back.setOnClickListener { mode("login") }
        findViewById<View>(R.id.forgotPassword).setOnClickListener {
            status.text = "Password recovery is being prepared for TOCO accounts."
        }

        val id = findViewById<EditText>(R.id.tocoId)
        val password = findViewById<EditText>(R.id.tocoPassword)
        findViewById<View>(R.id.tocoLogin).setOnClickListener {
            loading(true, "Signing you in…")
            auth.signInTocoId(id.text.toString(), password.text.toString(), ::handle)
        }

        val createUsername = findViewById<EditText>(R.id.createUsername)
        val createPassword = findViewById<EditText>(R.id.createPassword)
        val preview = findViewById<TextView>(R.id.idPreview)
        createUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val clean = s?.toString()?.trim()?.lowercase()?.replace("@toco.io", "") ?: ""
                preview.text = if (clean.isBlank()) "yourname@toco.io" else "$clean@toco.io"
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        findViewById<View>(R.id.tocoCreate).setOnClickListener {
            val clean = createUsername.text.toString().trim().lowercase().replace("@toco.io", "")
            loading(true, "Creating your TOCO ID…")
            auth.createTocoId("$clean@toco.io", createPassword.text.toString(), ::handle)
        }

        val phone = findViewById<EditText>(R.id.phoneNumber)
        findViewById<View>(R.id.sendPhoneCode).setOnClickListener {
            loading(true, "Sending verification code…")
            auth.sendPhoneCode(phone.text.toString()) { result ->
                loading(false, result.message)
                if (result.ok) {
                    phoneCode.visibility = View.VISIBLE
                    verifyPhone.visibility = View.VISIBLE
                    phoneCode.requestFocus()
                }
            }
        }
        verifyPhone.setOnClickListener {
            loading(true, "Verifying your account…")
            auth.verifyPhoneCode(phone.text.toString(), phoneCode.text.toString(), ::handle)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        intent.data?.let { uri ->
            if (uri.scheme == "toco") {
                val result = auth.acceptOAuthCallback(uri)
                if (result.ok) goNext() else loading(false, result.message)
            }
        }
    }

    private fun handle(result: AuthManager.Result) { loading(false, result.message); if (result.ok) goNext() }
    private fun loading(on: Boolean, message: String) {
        if (!::status.isInitialized) return
        progress.visibility = if (on) View.VISIBLE else View.GONE
        status.text = message
    }
    private fun goNext() {
        val destination = if (Prefs(this).onboardingDone) MainActivity::class.java else OnboardingActivity::class.java
        startActivity(Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish()
    }
}

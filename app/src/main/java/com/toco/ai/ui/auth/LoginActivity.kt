package com.toco.ai.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
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
    private lateinit var verifyPhone: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = AuthManager(this)

        // Google OAuth returns here through toco://auth/callback.
        if (intent?.data?.scheme == "toco") {
            val result = auth.acceptOAuthCallback(intent.data!!)
            if (result.ok) {
                goNext()
                return
            }
        }

        if (auth.isSignedIn) {
            goNext()
            return
        }

        setContentView(R.layout.activity_login)
        status = findViewById(R.id.loginStatus)
        progress = findViewById(R.id.loginProgress)
        phoneCode = findViewById(R.id.phoneCode)
        verifyPhone = findViewById(R.id.verifyPhone)

        findViewById<View>(R.id.googleLogin).setOnClickListener {
            startActivity(auth.googleIntent())
        }

        val id = findViewById<EditText>(R.id.tocoId)
        val password = findViewById<EditText>(R.id.tocoPassword)
        findViewById<View>(R.id.tocoLogin).setOnClickListener {
            loading(true, "Signing in securely…")
            auth.signInTocoId(id.text.toString(), password.text.toString(), ::handle)
        }
        findViewById<View>(R.id.tocoCreate).setOnClickListener {
            loading(true, "Creating your TOCO ID…")
            auth.createTocoId(id.text.toString(), password.text.toString(), ::handle)
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
            loading(true, "Verifying…")
            auth.verifyPhoneCode(phone.text.toString(), phoneCode.text.toString(), ::handle)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            if (uri.scheme == "toco") {
                val result = auth.acceptOAuthCallback(uri)
                if (result.ok) goNext() else loading(false, result.message)
            }
        }
    }

    private fun handle(result: AuthManager.Result) {
        loading(false, result.message)
        if (result.ok) goNext()
    }

    private fun loading(on: Boolean, message: String) {
        if (!::status.isInitialized) return
        progress.visibility = if (on) View.VISIBLE else View.GONE
        status.text = message
    }

    private fun goNext() {
        val destination = if (Prefs(this).onboardingDone) MainActivity::class.java else OnboardingActivity::class.java
        startActivity(Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}

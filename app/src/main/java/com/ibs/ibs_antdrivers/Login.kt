package com.ibs.ibs_antdrivers

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.Executor

class Login : AppCompatActivity() {

    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var login: Button
    private lateinit var auth: FirebaseAuth

    // Biometric fields (explicit types avoid inference errors)
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // ---- UI ----
        username = findViewById(R.id.loginUsername)
        password = findViewById(R.id.loginPassword)
        login = findViewById(R.id.loginButton)
        auth = FirebaseAuth.getInstance()

        login.setOnClickListener {
            val email = username.text.toString().trim()
            val pass = password.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
            } else {
                signInUser(email, pass, postLoginAskBiometrics = true)
            }
        }

        // ---- Biometric setup (AndroidX) ----
        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val email = SecurePrefs.getEmail(this@Login)
                    val pass = SecurePrefs.getPassword(this@Login)
                    if (!email.isNullOrBlank() && !pass.isNullOrBlank()) {
                        // Silent Firebase sign-in with stored creds
                        signInUser(email, pass, postLoginAskBiometrics = false)
                    } else {
                        Toast.makeText(
                            this@Login,
                            "Saved credentials unavailable. Please sign in.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User canceled or other error → do nothing; password UI stays.
                }

                override fun onAuthenticationFailed() {
                    // A single failed attempt; prompt remains until user cancels.
                }
            }
        )

        // Allow fingerprint and face (Android chooses; fingerprint typically first if both exist)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login")
            .setSubtitle("Use your fingerprint or face to sign in")
            .setNegativeButtonText("Use password") // graceful fallback
            .setAllowedAuthenticators(authenticators)
            .build()

        // Auto-prompt biometrics if eligible; otherwise show normal login
        maybeAutoPromptBiometrics(authenticators)
    }

    private fun maybeAutoPromptBiometrics(authenticators: Int) {
        val hasCreds = !SecurePrefs.getEmail(this).isNullOrBlank() &&
                !SecurePrefs.getPassword(this).isNullOrBlank()

        val bm = BiometricManager.from(this)
        val canUse = bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

        if (hasCreds && SecurePrefs.isBiometricEnabled(this) && canUse) {
            biometricPrompt.authenticate(promptInfo)
        }
    }


    private fun signInUser(email: String, password: String, postLoginAskBiometrics: Boolean = false) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    if (postLoginAskBiometrics) {
                        maybeOfferToEnableBiometrics(email, password)
                    } else {
                        goToMain()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Login failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun maybeOfferToEnableBiometrics(email: String, pass: String) {
        // Only offer if device actually supports biometrics
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        val canUse = BiometricManager.from(this)
            .canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canUse) {
            goToMain(); return
        }

        if (!SecurePrefs.isBiometricEnabled(this)) {
            AlertDialog.Builder(this)
                .setTitle("Enable biometric login?")
                .setMessage("Use your fingerprint or face to sign in faster next time.")
                .setPositiveButton("Enable") { _: DialogInterface, _: Int ->
                    SecurePrefs.saveCreds(this, email, pass)
                    SecurePrefs.setBiometricEnabled(this, true)
                    goToMain()
                }
                .setNegativeButton("Not now") { _, _ ->
                    goToMain()
                }
                .setCancelable(false)
                .show()
        } else {
            // Already enabled — refresh saved creds (e.g., changed password)
            SecurePrefs.saveCreds(this, email, pass)
            goToMain()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

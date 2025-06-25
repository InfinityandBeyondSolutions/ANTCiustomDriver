package com.ibs.ibs_antdrivers

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.ibs.ibs_antdrivers.BuildConfig
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


class RegisterActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var firstName: EditText
    private lateinit var surname: EditText
    private lateinit var mobileNum: EditText
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var regButton: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase

    /*val adminPrefix = BuildConfig.ADMIN_PREFIX
    val employeePrefix = BuildConfig.EMPLOYEE_PREFIX*/

    val adminPrefix: String = BuildConfig.ADMIN_PREFIX
    val employeePrefix: String = BuildConfig.EMPLOYEE_PREFIX


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        firstName = findViewById(R.id.regFName)
        surname = findViewById(R.id.regLastName)
        mobileNum = findViewById(R.id.regPhoneNumber)
        email = findViewById(R.id.regEmail)
        password = findViewById(R.id.regPassword)

        regButton = findViewById(R.id.registerButton)
        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance()


        regButton.setOnClickListener {
            val fName = firstName.text.toString().trim()
            val lName = surname.text.toString().trim()
            val phone = mobileNum.text.toString().trim()
            val emailText = email.text.toString().trim()
            val fullPassword = password.text.toString().trim()

            val role: String?
            val actualPassword: String

            when {
                fullPassword.startsWith(adminPrefix) -> {
                    role = "admin"
                    actualPassword = fullPassword.removePrefix(adminPrefix)
                }

                fullPassword.startsWith(employeePrefix) -> {
                    role = "employee"
                    actualPassword = fullPassword.removePrefix(employeePrefix)
                }

                else -> {
                    Toast.makeText(this, "Invalid password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            if (actualPassword.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(emailText, actualPassword)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid
                        val userMap = mapOf(
                            "firstName" to fName,
                            "lastName" to lName,
                            "phone" to phone,
                            "email" to emailText,
                            "role" to role
                        )
                        db.reference.child("users").child(uid!!).setValue(userMap)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Registered as $role", Toast.LENGTH_SHORT)
                                    .show()

                                // Navigate back to Login screen
                                val intent = Intent(this, Login::class.java) // <-- Replace Login with your actual Login activity class name
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                                finish()

                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "DB Error: ${it.message}", Toast.LENGTH_SHORT)
                                    .show()
                            }
                    } else {
                        Toast.makeText(
                            this,
                            "Auth failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

    }

}
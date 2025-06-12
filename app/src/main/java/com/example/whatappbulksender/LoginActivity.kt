package com.example.whatappbulksender

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoginActivity : AppCompatActivity() {
    private lateinit var emailInput: TextInputEditText
    private lateinit var passcodeInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var db: FirebaseFirestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        db = FirebaseFirestore.getInstance()
        emailInput = findViewById(R.id.emailInput)
        passcodeInput = findViewById(R.id.passcodeInput)
        loginButton = findViewById(R.id.loginButton)

        // Set click listener for login button
        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val passcode = passcodeInput.text.toString().trim()

            if (email.isEmpty()) {
                emailInput.error = "Email required"
                return@setOnClickListener
            }

            if (passcode.length != 6) {
                passcodeInput.error = "Enter 6-digit passcode"
                return@setOnClickListener
            }
            db.collection("user")
                .whereEqualTo("email", email)
                .whereEqualTo("passcode", passcode)
                .get()
                .addOnSuccessListener { documents: QuerySnapshot ->
                    Log.d("MyApp", "onCreate: "+documents)
                    if (documents.isEmpty) {
                        Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val userDoc = documents.documents[0]
                    val isActive = userDoc.getBoolean("active") ?: false

                    if (!isActive) {
                        Toast.makeText(this, "Account is not active", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()


                        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                            Date()
                        )
                        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit {
                            putString("user_email", email)
                            putString("user_passcode",passcode)
                            putString("lastCheckedDate", currentDate)
                        }

                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        }


    }
}
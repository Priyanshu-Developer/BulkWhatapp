package com.example.whatappbulksender

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit

@SuppressLint("CustomSplashScreen")
class SplashScreen : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val email = prefs.getString("user_email", null)
        val passcode = prefs.getString("user_passcode", null)

        if (email.isNullOrEmpty() || passcode.isNullOrEmpty()) {
            goToLogin()
            return
        }

        val lastCheckedDate = prefs.getString("lastCheckedDate", "")
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (lastCheckedDate != currentDate) {
            db.collection("user")
                .whereEqualTo("email", email)
                .whereEqualTo("passcode", passcode)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.isEmpty) {
                        Toast.makeText(this, "Account not found", Toast.LENGTH_SHORT).show()
                        clearPrefsAndLogin()
                        return@addOnSuccessListener
                    }

                    val userDoc = snapshot.documents[0]
                    val isActive = userDoc.getBoolean("active") ?: false

                    if (!isActive) {
                        Toast.makeText(this, "Account inactive", Toast.LENGTH_SHORT).show()
                        clearPrefsAndLogin()
                    } else {
                        prefs.edit { putString("lastCheckedDate", currentDate) }
                        goToMain()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show()
                    goToLogin()
                }
        } else {
            // Already checked today, proceed directly
            goToMain()
        }
    }
    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun clearPrefsAndLogin() {
        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit { clear() }
        goToLogin()
    }
}
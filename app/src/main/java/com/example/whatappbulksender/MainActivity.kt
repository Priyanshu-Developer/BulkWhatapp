package com.example.whatappbulksender

import android.content.Intent
import android.icu.util.Calendar
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnCheckPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        db = FirebaseFirestore.getInstance()

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            // ⏰ Time check: Allow only between 9 PM to 10 PM
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)

            if (hour < 21 || hour >= 22) {
                Toast.makeText(this, "Message sending is only allowed between 9 PM and 10 PM", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Enable accessibility service first", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }

            // ✅ Send broadcast to start message sending
            val intent = Intent("com.example.whatappbulksender.SEND_MESSAGE")
            sendBroadcast(intent)
        }

    }
    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${WhatsAppAccessibilityService::class.java.canonicalName}"
        val enabledServices =
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

        return enabledServices != null && enabledServices.contains(service)
    }

}

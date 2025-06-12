package com.example.whatappbulksender

import android.content.Intent
import android.icu.util.Calendar
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var recipients: TextView
    private  lateinit var estimatedTime : TextView
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()
        recipients = findViewById(R.id.tvRecipientCount)
        estimatedTime = findViewById(R.id.estimatedTime)

        findViewById<Button>(R.id.btnStartSending).setOnClickListener {
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

        // 👇 Fetch and display unique user count
        fetchUniqueUserCountFor9to10PM { count ->
            "$count contacts".also { recipients.text = it }
            val totaltime= count*5
            "~ $totaltime seconds ".also { estimatedTime.text = it }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${WhatsAppAccessibilityService::class.java.canonicalName}"
        val enabledServices =
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices != null && enabledServices.contains(service)
    }

    private fun fetchUniqueUserCountFor9to10PM(onResult: (Int) -> Unit) {
        val calendarStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val calendarEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startTimestamp = Timestamp(calendarStart.time)
        val endTimestamp = Timestamp(calendarEnd.time)

        db.collection("whatsapp")
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThan("timestamp", endTimestamp)
            .get()
            .addOnSuccessListener { snapshot ->
                val uniqueUsers = mutableSetOf<String>()
                for (doc in snapshot.documents) {
                    val userId = doc.getString("userId") ?: continue
                    uniqueUsers.add(userId)
                }
                onResult(uniqueUsers.size)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to fetch user count", Toast.LENGTH_SHORT).show()
                onResult(0)
            }
    }
}

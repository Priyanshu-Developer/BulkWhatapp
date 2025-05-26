package com.example.whatappbulksender

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private var filePath: Uri? = null
    private val PICK_CSV_FILE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnCheckPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnPickCSV).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, PICK_CSV_FILE)
        }

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            filePath?.let {
                // Send broadcast with file URI
                val intent = Intent("com.example.whatappbulksender.SEND_CSV")
                intent.putExtra("CSV_URI", it.toString())
                sendBroadcast(intent)
            } ?: run {
                Toast.makeText(this, "Please select a CSV file first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PICK_CSV_FILE && resultCode == RESULT_OK) {
            data?.data?.let {
                filePath = it
                Toast.makeText(this, "CSV file selected", Toast.LENGTH_SHORT).show()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}

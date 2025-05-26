package com.example.whatappbulksender

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import java.util.*

class WhatsAppAccessibilityService : AccessibilityService() {

    private val pendingMessages: Queue<Pair<String, String>> = LinkedList()
    private var isSending = false
    private var handler: Handler = Handler()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onServiceConnected() {
        Log.d("WAService", "Accessibility Service connected")
        registerReceiver()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun registerReceiver() {
        val filter = IntentFilter("com.example.whatappbulksender.SEND_CSV")
        registerReceiver(csvReceiver, filter, Context.RECEIVER_EXPORTED)
    }


    private val csvReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val uriString = intent.getStringExtra("CSV_URI")
            uriString?.let {
                val uri = Uri.parse(it)
                processCsv(uri)
            }
        }
    }

    fun processCsv(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        pendingMessages.clear()

        reader.forEachLine { line ->
            val parts = line.split(",")
            if (parts.size >= 2) {
                val phone = parts[0].trim()
                val message = parts[1].trim()
                pendingMessages.add(Pair(phone, message))
            }
        }

        Log.d("WAService", "Queued ${pendingMessages.size} messages")
        sendNext()
    }

    private fun sendNext() {
        if (pendingMessages.isNotEmpty()) {
            val (phone, message) = pendingMessages.peek()
            val url = "https://wa.me/$phone?text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Log.d("WAService", "Opening chat for $phone")
            startActivity(intent)
            isSending = true
        } else {
            Log.d("WAService", "All messages sent")
            isSending = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (event.packageName?.contains("com.whatsapp") == true) {
            val rootNode = rootInActiveWindow ?: return
            val sent = clickSendButton(rootNode)

            if (sent) {
                pendingMessages.poll() // Remove current message
                handler.postDelayed({ sendNext() }, 3000)
            }
        }
    }

    override fun onInterrupt() {}

    private fun clickSendButton(node: AccessibilityNodeInfo): Boolean {
        val sendButtons = node.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (sendButtons.isNotEmpty()) {
            sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d("WAService", "Send button clicked")
            return true
        } else {
            Log.d("WAService", "Send button not found")
            return false
        }
    }
}

package com.example.whatappbulksender

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.net.URLEncoder
import java.util.*

class WhatsAppAccessibilityService : AccessibilityService() {

    private val pendingMessages: Queue<Triple<String, String, String>> = LinkedList()
    private var isSending = false
    private var handler: Handler = Handler()

    private val db = FirebaseFirestore.getInstance()
    private var lastVisible: DocumentSnapshot? = null
    private val batchSize = 100
    private var sharedPref: SharedPreferences? = null
    private var lastSentId: String? = null
    private var isFetching = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onServiceConnected() {
        Log.d("WAService", "Accessibility Service connected")
        sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        lastSentId = sharedPref?.getString("last_sent_id", null)

        registerReceiver()
        fetchNextBatch()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun registerReceiver() {
        val filter = IntentFilter("com.example.whatappbulksender.SEND_MESSAGE")
        registerReceiver(broadcastReceiver, filter, RECEIVER_EXPORTED)
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            fetchNextBatch()
        }
    }

    private fun fetchNextBatch() {
        if (isFetching) return
        isFetching = true

        // 🔽 Get today's date and set bounds for 9 PM to 10 PM
        val calendarStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21) // 9 PM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val calendarEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22) // 10 PM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startTimestamp = com.google.firebase.Timestamp(calendarStart.time)
        val endTimestamp = com.google.firebase.Timestamp(calendarEnd.time)

        var query = db.collection("whatsapp")
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThan("timestamp", endTimestamp)
            .orderBy("timestamp")
            .limit(batchSize.toLong())

        lastVisible?.let {
            query = query.startAfter(it)
        }

        query.get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                lastVisible = snapshot.documents.last()

                for (doc in snapshot.documents) {
                    val id = doc.id
                    val number = doc.getString("number") ?: continue
                    val message = doc.getString("message") ?: continue
                    val status = doc.getString("status")

                    if (status == "sent") continue
                    if (id == lastSentId) continue

                    pendingMessages.add(Triple(id, number, message))
                }

                if (!isSending) {
                    sendNext()
                }
            } else {
                Log.d("WAService", "No messages in the 9–10 PM time window")
            }
            isFetching = false
        }.addOnFailureListener {
            Log.e("WAService", "Failed to fetch: ${it.message}")
            isFetching = false
        }
    }


    private fun sendNext() {
        if (pendingMessages.isEmpty()) {
            fetchNextBatch()
            return
        }

        val (id, phone, message) = pendingMessages.peek()

        try {
            val url = "https://wa.me/$phone?text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Log.d("WAService", "Opening WhatsApp chat for $phone")
            startActivity(intent)
            isSending = true
        } catch (e: Exception) {
            logFailureToFirestore(id, phone, message, e.message ?: "Unknown error")
            pendingMessages.poll()
            handler.postDelayed({ sendNext() }, 3000)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (event.packageName?.contains("com.whatsapp") == true) {
            val rootNode = rootInActiveWindow ?: return
            val sent = clickSendButton(rootNode)

            if (sent) {
                val (id, phone, message) = pendingMessages.poll()

                // ✅ Update Firestore with sent status
                db.collection("whatsapp").document(id)
                    .update(
                        mapOf(
                            "status" to "sent",
                            "sent_at" to FieldValue.serverTimestamp()
                        )
                    )
                    .addOnSuccessListener {
                        Log.d("WAService", "Marked $phone as sent")
                    }
                    .addOnFailureListener {
                        Log.e("WAService", "Failed to mark $phone as sent: ${it.message}")
                    }

                // ✅ Save progress
                sharedPref?.edit()?.putString("last_sent_id", id)?.apply()
                handler.postDelayed({ sendNext() }, 3000)
            }
        }
    }

    private fun clickSendButton(node: AccessibilityNodeInfo): Boolean {
        val sendButtons = node.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        return if (sendButtons.isNotEmpty()) {
            sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d("WAService", "Send button clicked")
            true
        } else {
            Log.d("WAService", "Send button not found")
            false
        }
    }

    private fun logFailureToFirestore(id: String, phone: String, message: String, error: String) {
        val errorUpdate = mapOf(
            "status" to "failed",
            "error" to error,
            "failed_at" to FieldValue.serverTimestamp()
        )

        db.collection("whatsapp").document(id)
            .update(errorUpdate)
            .addOnSuccessListener {
                Log.d("WAService", "Logged failure for $phone")
            }
            .addOnFailureListener {
                Log.e("WAService", "Failed to log error for $phone: ${it.message}")
            }
    }

    override fun onInterrupt() {}
}

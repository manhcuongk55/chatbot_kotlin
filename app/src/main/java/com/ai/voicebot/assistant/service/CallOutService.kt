package com.ai.voicebot.assistant.service

import android.content.Intent
import android.util.Log
import com.ai.voicebot.assistant.ui.KeyboardActivity
import com.ai.voicebot.assistant.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CallOutService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d(TAG, "From: ${remoteMessage?.from}")

        // Check if message contains a data payload.
        remoteMessage?.data?.isNotEmpty()?.let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
            KeyboardActivity.phoneNumber = remoteMessage.data.get("phoneNumber")?.toIntOrNull()!!
            if(KeyboardActivity.phoneNumber != null && KeyboardActivity.phoneNumber > 0){
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent);
            }
        }

        // Check if message contains a notification payload.
        remoteMessage?.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            KeyboardActivity.phoneNumber = remoteMessage.notification!!.body?.toIntOrNull()!!
            if(KeyboardActivity.phoneNumber != null && KeyboardActivity.phoneNumber > 0){
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent);
            }
        }

    }
    override fun onNewToken(token: String?) {
        Log.d(TAG, "Refreshed token: $token")

    }
    companion object {
        private const val TAG = "CallOutService"

    }
}

package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.SafeGuardApplication
import com.example.data.repository.SafetyRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SafeGuardMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (Firebase.auth.currentUser != null) {
            CoroutineScope(Dispatchers.IO).launch {
                SafetyRepository(applicationContext).updateFcmToken(token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "SafeGuard Emergency Alert"
        val body = message.notification?.body ?: message.data["body"] ?: "A trusted contact needs attention."
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, SafeGuardApplication.CHANNEL_EMERGENCY_FCM)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notif)
    }
}

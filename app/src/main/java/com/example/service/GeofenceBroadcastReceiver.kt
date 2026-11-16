package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.SafeGuardApplication
import com.example.data.repository.SafetyRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transition = event.geofenceTransition
        val transitionLabel = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "entered"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "exited"
            else -> return
        }

        val triggered = event.triggeringGeofences.orEmpty()
        if (triggered.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SafetyRepository(context.applicationContext)
                val isSignedIn = Firebase.auth.currentUser != null
                triggered.forEach { gf ->
                    val parts = gf.requestId.split("|")
                    val zoneId = parts.getOrElse(0) { gf.requestId }
                    val zoneName = parts.getOrElse(1) { "Safety Zone" }
                    val zoneType = parts.getOrElse(2) { "safe_zone" }

                    if (isSignedIn && zoneId.isNotBlank()) {
                        repo.updateGeofenceStatus(zoneId, lastEvent = transitionLabel)
                    }
                    postAlertNotification(context, zoneName, zoneType, transitionLabel)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postAlertNotification(
        context: Context,
        zoneName: String,
        zoneType: String,
        transitionLabel: String,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (zoneType == "restricted_zone" && transitionLabel == "entered") {
            "Danger/Restricted Zone Entered: $zoneName"
        } else {
            "Geofence Alert: ${transitionLabel.replaceFirstChar { it.uppercase() }} $zoneName"
        }
        val notif = NotificationCompat.Builder(context, SafeGuardApplication.CHANNEL_GEOFENCE_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Tap to review live tracking or trigger SOS.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        manager.notify(zoneName.hashCode(), notif)
    }
}

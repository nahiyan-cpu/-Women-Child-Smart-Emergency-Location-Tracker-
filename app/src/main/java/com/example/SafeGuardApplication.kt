package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SafeGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            val sosChannel = NotificationChannel(
                CHANNEL_SOS_ACTIVE,
                "Active SOS & Live Tracking",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Persistent notification shown while live SOS location sharing is active."
                setShowBadge(true)
            }

            val geofenceChannel = NotificationChannel(
                CHANNEL_GEOFENCE_ALERTS,
                "Geofence & Danger Zone Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when entering or exiting configured safety geofences."
            }

            val fcmChannel = NotificationChannel(
                CHANNEL_EMERGENCY_FCM,
                "Emergency Push Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming emergency alerts from trusted contacts and responders."
            }

            manager.createNotificationChannels(listOf(sosChannel, geofenceChannel, fcmChannel))
        }
    }

    companion object {
        const val CHANNEL_SOS_ACTIVE = "safeguard_sos_active"
        const val CHANNEL_GEOFENCE_ALERTS = "safeguard_geofence_alerts"
        const val CHANNEL_EMERGENCY_FCM = "safeguard_emergency_fcm"
    }
}

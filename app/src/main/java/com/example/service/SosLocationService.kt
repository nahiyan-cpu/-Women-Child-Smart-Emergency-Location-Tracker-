package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.SafeGuardApplication
import com.example.data.repository.SafetyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class SosLocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null
    private lateinit var locationManager: LocationAndGeofenceManager
    private lateinit var repository: SafetyRepository

    override fun onCreate() {
        super.onCreate()
        locationManager = LocationAndGeofenceManager(applicationContext)
        repository = SafetyRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val incidentId = intent.getStringExtra(EXTRA_INCIDENT_ID).orEmpty()
                val shareId = intent.getStringExtra(EXTRA_SHARE_ID).orEmpty()
                val sirenActive = intent.getBooleanExtra(EXTRA_SIREN_ACTIVE, false)
                startForegroundCompat(buildNotification(incidentId, shareId))
                if (incidentId.isNotBlank()) {
                    startLocationStream(incidentId, shareId, sirenActive)
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationStream(incidentId: String, shareId: String, sirenActive: Boolean) {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            locationManager.observeLocationUpdates(intervalMs = 6000L)
                .catch { /* Permission revoked or GPS unavailable; keep service clean */ }
                .collect { snapshot ->
                    val battery = locationManager.getBatteryPercent()
                    repository.pushSosLocationUpdate(
                        incidentId = incidentId,
                        shareId = shareId,
                        location = snapshot,
                        batteryPercent = battery,
                        sirenTriggered = sirenActive,
                    )
                }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(incidentId: String, shareId: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SafeGuardApplication.CHANNEL_SOS_ACTIVE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SafeGuard SOS Live Tracking Active")
            .setContentText(
                if (shareId.isNotBlank()) "Beacon $shareId streaming real-time GPS to trusted contacts"
                else "Emergency incident $incidentId active"
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        trackingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 9110
        const val ACTION_START = "com.example.action.START_SOS_TRACKING"
        const val ACTION_STOP = "com.example.action.STOP_SOS_TRACKING"
        const val EXTRA_INCIDENT_ID = "extra_incident_id"
        const val EXTRA_SHARE_ID = "extra_share_id"
        const val EXTRA_SIREN_ACTIVE = "extra_siren_active"

        fun start(context: Context, incidentId: String, shareId: String, sirenActive: Boolean) {
            val intent = Intent(context, SosLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INCIDENT_ID, incidentId)
                putExtra(EXTRA_SHARE_ID, shareId)
                putExtra(EXTRA_SIREN_ACTIVE, sirenActive)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SosLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.model.LocationSnapshot
import com.example.data.model.SafetyGeofence
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

class LocationAndGeofenceManager(private val context: Context) {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val geofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationServiceEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun getBatteryPercent(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 100
        } catch (_: Exception) {
            100
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<LocationSnapshot> = runCatching {
        if (!hasLocationPermission()) {
            throw SecurityException("Location permission not granted")
        }
        val current = try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        } catch (_: Exception) {
            null
        }
        val loc = current ?: fusedClient.lastLocation.await()
            ?: throw IllegalStateException("GPS fix unavailable. Ensure location services are enabled.")
        LocationSnapshot(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = loc.accuracy,
            speedMps = loc.speed,
            provider = loc.provider ?: "fused",
            timestampMillis = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
    }

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(intervalMs: Long = 5000L): Flow<LocationSnapshot> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission required"))
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(2500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    LocationSnapshot(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMeters = loc.accuracy,
                        speedMps = loc.speed,
                        provider = loc.provider ?: "fused",
                        timestampMillis = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    )
                )
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { close(it) }

        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    suspend fun syncGeofencesWithOs(zones: List<SafetyGeofence>): Result<Int> = runCatching {
        if (!hasFineLocationPermission()) {
            throw SecurityException("Precise location permission is required for OS geofencing.")
        }
        val activeZones = zones.filter { it.isActive }
        val pendingIntent = geofencePendingIntent()
        geofencingClient.removeGeofences(pendingIntent).await()
        if (activeZones.isEmpty()) return@runCatching 0

        val osGeofences = activeZones.take(50).mapNotNull { zone ->
            val transitions = (if (zone.notifyOnEnter) Geofence.GEOFENCE_TRANSITION_ENTER else 0) or
                (if (zone.notifyOnExit) Geofence.GEOFENCE_TRANSITION_EXIT else 0)
            if (transitions == 0) return@mapNotNull null
            Geofence.Builder()
                .setRequestId("${zone.id}|${zone.name}|${zone.zoneType}")
                .setCircularRegion(
                    zone.latitude,
                    zone.longitude,
                    zone.radiusMeters.toFloat().coerceIn(50f, 10000f)
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(transitions)
                .build()
        }
        if (osGeofences.isEmpty()) return@runCatching 0

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(osGeofences)
            .build()

        geofencingClient.addGeofences(request, pendingIntent).await()
        osGeofences.size
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            GEOFENCE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    companion object {
        private const val GEOFENCE_REQUEST_CODE = 4201

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0]
        }

        fun formatDistance(meters: Float): String {
            return if (meters < 1000f) {
                "${meters.roundToInt()} m"
            } else {
                String.format("%.2f km", meters / 1000f)
            }
        }
    }
}

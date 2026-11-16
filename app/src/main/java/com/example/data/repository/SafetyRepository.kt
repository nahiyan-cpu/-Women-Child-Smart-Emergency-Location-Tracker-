package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DangerZone
import com.example.data.model.EmergencyContact
import com.example.data.model.EvidenceRecord
import com.example.data.model.LiveShareBeacon
import com.example.data.model.LocationBreadcrumb
import com.example.data.model.LocationSnapshot
import com.example.data.model.SafePlace
import com.example.data.model.SafetyGeofence
import com.example.data.model.SosGlobalAlert
import com.example.data.model.SosIncident
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SafetyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.handleStreamErrors(
        uid: String,
        operationType: OperationType,
        path: String,
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // --- USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .handleStreamErrors(userId, OperationType.GET, docRef.path)
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String?,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val profile = UserProfile(
                    userId = uid,
                    displayName = displayName.ifBlank { "SafeGuard User" },
                    email = email,
                    sosAutoSiren = true,
                    locationSharingEnabled = true,
                )
                docRef.set(profile.toCreateMap(uid)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(profile.toUpdateMap()).await()
            } else {
                docRef.set(profile.toCreateMap(uid)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateFcmToken(token: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "fcmToken" to token.take(512),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- EMERGENCY CONTACTS ---

    fun observeEmergencyContacts(userId: String): Flow<List<EmergencyContact>> = flow {
        val colRef = db.collection("users").document(userId).collection("emergency_contacts")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(EmergencyContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareByDescending<EmergencyContact> { it.isPrimary }.thenBy { it.name.lowercase() })
                }
                .handleStreamErrors(userId, OperationType.LIST, colRef.path)
        )
    }

    suspend fun addEmergencyContact(contact: EmergencyContact): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("emergency_contacts").document()
        try {
            docRef.set(contact.toCreateMap(uid)).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateEmergencyContact(contact: EmergencyContact): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(contact.id.isNotBlank()) { "Contact ID required for update" }
        val docRef = db.collection("users").document(uid).collection("emergency_contacts").document(contact.id)
        try {
            docRef.update(contact.toUpdateMap()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteEmergencyContact(contactId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("emergency_contacts").document(contactId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- GEOFENCES ---

    fun observeGeofences(userId: String): Flow<List<SafetyGeofence>> = flow {
        val colRef = db.collection("users").document(userId).collection("geofences")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(SafetyGeofence::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .handleStreamErrors(userId, OperationType.LIST, colRef.path)
        )
    }

    suspend fun addGeofence(geofence: SafetyGeofence): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("geofences").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to geofence.name.trim().take(60),
            "zoneType" to geofence.zoneType,
            "latitude" to geofence.latitude,
            "longitude" to geofence.longitude,
            "radiusMeters" to geofence.radiusMeters.coerceIn(50.0, 10000.0),
            "isActive" to geofence.isActive,
            "notifyOnEnter" to geofence.notifyOnEnter,
            "notifyOnExit" to geofence.notifyOnExit,
            "lastEvent" to geofence.lastEvent,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateGeofenceStatus(
        geofenceId: String,
        isActive: Boolean? = null,
        lastEvent: String? = null,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("geofences").document(geofenceId)
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        if (isActive != null) updates["isActive"] = isActive
        if (lastEvent != null) updates["lastEvent"] = lastEvent
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteGeofence(geofenceId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("geofences").document(geofenceId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- SOS INCIDENTS, LIVE SHARE BEACONS & BREADCRUMBS ---

    fun observeSosIncidents(userId: String): Flow<List<SosIncident>> = flow {
        val colRef = db.collection("users").document(userId).collection("sos_incidents")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(SosIncident::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .handleStreamErrors(userId, OperationType.LIST, colRef.path)
        )
    }

    suspend fun triggerSosIncident(
        userAlias: String,
        triggerType: String,
        location: LocationSnapshot,
        addressLabel: String,
        sirenTriggered: Boolean,
        batteryPercent: Int,
        notes: String,
    ): Result<SosIncident> = runCatching {
        val uid = requireUserId()
        val incidentId = "sos_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val shareId = "shr_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        val incidentRef = db.collection("users").document(uid).collection("sos_incidents").document(incidentId)
        val shareRef = db.collection("live_shares").document(shareId)
        val globalAlertRef = db.collection("sos_global_alerts").document(incidentId)
        val crumbRef = incidentRef.collection("location_breadcrumbs").document()

        val safeAlias = userAlias.trim().ifBlank { "SafeGuard Member" }.take(60)
        val safeBattery = batteryPercent.coerceIn(0, 100)
        val safeAcc = location.accuracyMeters.toDouble().coerceIn(0.0, 10000.0)
        val safeSpeed = location.speedMps.toDouble().coerceIn(0.0, 500.0)

        val batch = db.batch()
        batch.set(
            incidentRef,
            mapOf(
                "userId" to uid,
                "triggerType" to triggerType,
                "status" to "active",
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracyMeters" to safeAcc,
                "addressLabel" to addressLabel.take(200),
                "sirenTriggered" to sirenTriggered,
                "batteryPercent" to safeBattery,
                "notes" to notes.take(500),
                "evidenceCount" to 0,
                "shareCode" to shareId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            shareRef,
            mapOf(
                "ownerId" to uid,
                "ownerAlias" to safeAlias,
                "incidentId" to incidentId,
                "isActive" to true,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracyMeters" to safeAcc,
                "batteryPercent" to safeBattery,
                "statusMessage" to "Emergency SOS active - live coordinates streaming",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            globalAlertRef,
            mapOf(
                "incidentId" to incidentId,
                "userId" to uid,
                "userAlias" to safeAlias,
                "triggerType" to triggerType,
                "status" to "active",
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracyMeters" to safeAcc,
                "batteryPercent" to safeBattery,
                "sirenTriggered" to sirenTriggered,
                "shareId" to shareId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            crumbRef,
            mapOf(
                "userId" to uid,
                "incidentId" to incidentId,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "speedMps" to safeSpeed,
                "accuracyMeters" to safeAcc,
                "batteryPercent" to safeBattery,
                "recordedAt" to FieldValue.serverTimestamp(),
            )
        )

        try {
            batch.commit().await()
            SosIncident(
                id = incidentId,
                userId = uid,
                triggerType = triggerType,
                status = "active",
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = safeAcc,
                addressLabel = addressLabel,
                sirenTriggered = sirenTriggered,
                batteryPercent = safeBattery,
                notes = notes,
                evidenceCount = 0,
                shareCode = shareId,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, incidentRef.path)
            throw e
        }
    }

    suspend fun pushSosLocationUpdate(
        incidentId: String,
        shareId: String,
        location: LocationSnapshot,
        batteryPercent: Int,
        sirenTriggered: Boolean,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val incidentRef = db.collection("users").document(uid).collection("sos_incidents").document(incidentId)
        val shareRef = db.collection("live_shares").document(shareId)
        val globalAlertRef = db.collection("sos_global_alerts").document(incidentId)
        val crumbRef = incidentRef.collection("location_breadcrumbs").document()

        val safeBattery = batteryPercent.coerceIn(0, 100)
        val safeAcc = location.accuracyMeters.toDouble().coerceIn(0.0, 10000.0)
        val safeSpeed = location.speedMps.toDouble().coerceIn(0.0, 500.0)

        val batch = db.batch()
        batch.update(
            incidentRef,
            mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracyMeters" to safeAcc,
                "batteryPercent" to safeBattery,
                "sirenTriggered" to sirenTriggered,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        if (shareId.isNotBlank()) {
            batch.update(
                shareRef,
                mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracyMeters" to safeAcc,
                    "batteryPercent" to safeBattery,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }
        batch.update(
            globalAlertRef,
            mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracyMeters" to safeAcc,
                "batteryPercent" to safeBattery,
                "sirenTriggered" to sirenTriggered,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            crumbRef,
            mapOf(
                "userId" to uid,
                "incidentId" to incidentId,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "speedMps" to safeSpeed,
                "accuracyMeters" to safeAcc,
                "batteryPercent" to safeBattery,
                "recordedAt" to FieldValue.serverTimestamp(),
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, incidentRef.path)
            throw e
        }
    }

    suspend fun resolveSosIncident(
        incidentId: String,
        shareId: String,
        finalStatus: String = "resolved",
        notes: String? = null,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(finalStatus in listOf("resolved", "false_alarm")) { "Invalid terminal status" }
        val incidentRef = db.collection("users").document(uid).collection("sos_incidents").document(incidentId)
        val shareRef = db.collection("live_shares").document(shareId)
        val globalAlertRef = db.collection("sos_global_alerts").document(incidentId)

        val incidentUpdates = mutableMapOf<String, Any>(
            "status" to finalStatus,
            "sirenTriggered" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
            "resolvedAt" to FieldValue.serverTimestamp(),
        )
        if (notes != null) {
            incidentUpdates["notes"] = notes.take(500)
        }

        val batch = db.batch()
        batch.update(incidentRef, incidentUpdates)
        if (shareId.isNotBlank()) {
            batch.update(
                shareRef,
                mapOf(
                    "isActive" to false,
                    "statusMessage" to if (finalStatus == "false_alarm") "Marked safe (false alarm)" else "Incident resolved - user safe",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }
        batch.update(
            globalAlertRef,
            mapOf(
                "status" to finalStatus,
                "sirenTriggered" to false,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, incidentRef.path)
            throw e
        }
    }

    suspend fun getLiveShareBeacon(shareId: String): Result<LiveShareBeacon?> = runCatching {
        requireUserId()
        val docRef = db.collection("live_shares").document(shareId.trim())
        try {
            docRef.get().await().toObject(LiveShareBeacon::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    fun observeLiveShareBeacon(shareId: String): Flow<LiveShareBeacon?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("live_shares").document(shareId.trim())
        emitAll(
            docRef.snapshots()
                .map { it.toObject(LiveShareBeacon::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleStreamErrors(uid, OperationType.GET, docRef.path)
        )
    }

    // --- CONSENT-BASED ENCRYPTED EVIDENCE ---

    fun observeEvidenceRecords(userId: String): Flow<List<EvidenceRecord>> = flow {
        val colRef = db.collection("users").document(userId).collection("evidence_records")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(EvidenceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .handleStreamErrors(userId, OperationType.LIST, colRef.path)
        )
    }

    suspend fun addEvidenceRecord(record: EvidenceRecord): Result<String> = runCatching {
        val uid = requireUserId()
        require(record.consentConfirmed) { "Explicit user consent is mandatory before capturing or storing evidence." }
        val docRef = db.collection("users").document(uid).collection("evidence_records").document()
        val payload = mapOf(
            "userId" to uid,
            "incidentId" to record.incidentId.ifBlank { "general" }.take(128),
            "captureMode" to record.captureMode,
            "title" to record.title.trim().ifBlank { "Consented Evidence" }.take(100),
            "summary" to record.summary.trim().take(1000),
            "localEncryptedUri" to record.localEncryptedUri.take(500),
            "storagePath" to record.storagePath.take(500),
            "sha256Digest" to record.sha256Digest.ifBlank { "0000000000000000" }.take(128),
            "encryptionAlgo" to record.encryptionAlgo.ifBlank { "AES-256-GCM" }.take(40),
            "consentConfirmed" to true,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteEvidenceRecord(evidenceId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("evidence_records").document(evidenceId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- CROWDSOURCED DANGER ZONES ---

    fun observeVerifiedDangerZones(userId: String): Flow<List<DangerZone>> = flow {
        val query = db.collection("danger_zones").whereEqualTo("verificationStatus", "verified")
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(DangerZone::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.confirmCount }
                }
                .handleStreamErrors(userId, OperationType.LIST, "danger_zones")
        )
    }

    fun observeMyDangerZones(userId: String): Flow<List<DangerZone>> = flow {
        val query = db.collection("danger_zones").whereEqualTo("reporterId", userId)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(DangerZone::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .handleStreamErrors(userId, OperationType.LIST, "danger_zones")
        )
    }

    suspend fun reportDangerZone(
        reporterAlias: String,
        title: String,
        description: String,
        category: String,
        severity: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("danger_zones").document()
        val payload = mapOf(
            "reporterId" to uid,
            "reporterAlias" to reporterAlias.trim().ifBlank { "Community Member" }.take(60),
            "title" to title.trim().take(80),
            "description" to description.trim().take(300),
            "category" to category,
            "severity" to severity,
            "latitude" to latitude,
            "longitude" to longitude,
            "radiusMeters" to radiusMeters.coerceIn(50.0, 2000.0),
            "verificationStatus" to "pending",
            "confirmCount" to 1,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun confirmDangerZone(zoneId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("danger_zones").document(zoneId)
        try {
            docRef.update(
                mapOf(
                    "confirmCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- SAFE PLACES ---

    fun observeVerifiedSafePlaces(userId: String): Flow<List<SafePlace>> = flow {
        val query = db.collection("safe_places").whereEqualTo("verifiedByAdmin", true)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(SafePlace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() }
                }
                .handleStreamErrors(userId, OperationType.LIST, "safe_places")
        )
    }

    // --- ADMIN DASHBOARD OPERATIONS ---

    fun observeAllDangerZonesAdmin(userId: String): Flow<List<DangerZone>> = flow {
        val colRef = db.collection("danger_zones")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(DangerZone::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .handleStreamErrors(userId, OperationType.LIST, colRef.path)
        )
    }

    suspend fun moderateDangerZoneAdmin(zoneId: String, status: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("danger_zones").document(zoneId)
        try {
            docRef.update(
                mapOf(
                    "verificationStatus" to status,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeGlobalSosAlertsAdmin(userId: String): Flow<List<SosGlobalAlert>> = flow {
        val colRef = db.collection("sos_global_alerts")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(SosGlobalAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .handleStreamErrors(userId, OperationType.LIST, colRef.path)
        )
    }

    suspend fun updateGlobalSosStatusAdmin(
        alertId: String,
        targetUserId: String,
        incidentId: String,
        newStatus: String,
    ): Result<Unit> = runCatching {
        requireUserId()
        val globalRef = db.collection("sos_global_alerts").document(alertId)
        val userIncidentRef = db.collection("users").document(targetUserId).collection("sos_incidents").document(incidentId)
        val batch = db.batch()
        batch.update(
            globalRef,
            mapOf(
                "status" to newStatus,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        val incUpdates = mutableMapOf<String, Any>(
            "status" to newStatus,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (newStatus in listOf("resolved", "false_alarm")) {
            incUpdates["resolvedAt"] = FieldValue.serverTimestamp()
            incUpdates["sirenTriggered"] = false
        }
        batch.update(userIncidentRef, incUpdates)
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, globalRef.path)
            throw e
        }
    }

    suspend fun addSafePlaceAdmin(place: SafePlace): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("safe_places").document()
        val payload = mapOf(
            "name" to place.name.trim().take(100),
            "category" to place.category,
            "address" to place.address.trim().take(200),
            "phone" to place.phone.trim().take(32),
            "latitude" to place.latitude,
            "longitude" to place.longitude,
            "isOpen24Hours" to place.isOpen24Hours,
            "verifiedByAdmin" to true,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }
}

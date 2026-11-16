package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val bloodType: String? = null,
    val medicalNotes: String? = null,
    val homeAddress: String? = null,
    val emergencyPinHash: String? = null,
    val sosAutoSiren: Boolean = true,
    val locationSharingEnabled: Boolean = true,
    val fcmToken: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "displayName" to displayName.ifBlank { "SafeGuard User" }.take(80),
        "email" to email?.take(160),
        "phone" to phone?.takeIf { it.isNotBlank() }?.take(32),
        "bloodType" to bloodType?.takeIf { it.isNotBlank() }?.take(16),
        "medicalNotes" to medicalNotes?.takeIf { it.isNotBlank() }?.take(500),
        "homeAddress" to homeAddress?.takeIf { it.isNotBlank() }?.take(240),
        "emergencyPinHash" to emergencyPinHash?.takeIf { it.isNotBlank() }?.take(128),
        "sosAutoSiren" to sosAutoSiren,
        "locationSharingEnabled" to locationSharingEnabled,
        "fcmToken" to fcmToken?.takeIf { it.isNotBlank() }?.take(512),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    ).filterValues { it != null }.mapValues { it.value!! }

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "displayName" to displayName.ifBlank { "SafeGuard User" }.take(80),
        "email" to email?.take(160),
        "phone" to phone?.takeIf { it.isNotBlank() }?.take(32),
        "bloodType" to bloodType?.takeIf { it.isNotBlank() }?.take(16),
        "medicalNotes" to medicalNotes?.takeIf { it.isNotBlank() }?.take(500),
        "homeAddress" to homeAddress?.takeIf { it.isNotBlank() }?.take(240),
        "emergencyPinHash" to emergencyPinHash?.takeIf { it.isNotBlank() }?.take(128),
        "sosAutoSiren" to sosAutoSiren,
        "locationSharingEnabled" to locationSharingEnabled,
        "fcmToken" to fcmToken?.takeIf { it.isNotBlank() }?.take(512),
        "updatedAt" to FieldValue.serverTimestamp(),
    ).filterValues { it != null }.mapValues { it.value!! }
}

@IgnoreExtraProperties
data class EmergencyContact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val relationship: String = "",
    val phone: String = "",
    val email: String? = null,
    val isPrimary: Boolean = false,
    val notifyOnSos: Boolean = true,
    val notifyOnGeofence: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(80),
        "relationship" to relationship.trim().take(40),
        "phone" to phone.trim().take(32),
        "email" to email?.trim()?.takeIf { it.isNotBlank() }?.take(160),
        "isPrimary" to isPrimary,
        "notifyOnSos" to notifyOnSos,
        "notifyOnGeofence" to notifyOnGeofence,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    ).filterValues { it != null }.mapValues { it.value!! }

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "name" to name.trim().take(80),
        "relationship" to relationship.trim().take(40),
        "phone" to phone.trim().take(32),
        "email" to email?.trim()?.takeIf { it.isNotBlank() }?.take(160),
        "isPrimary" to isPrimary,
        "notifyOnSos" to notifyOnSos,
        "notifyOnGeofence" to notifyOnGeofence,
        "updatedAt" to FieldValue.serverTimestamp(),
    ).filterValues { it != null }.mapValues { it.value!! }
}

@IgnoreExtraProperties
data class SafetyGeofence(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val zoneType: String = "safe_zone",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 250.0,
    val isActive: Boolean = true,
    val notifyOnEnter: Boolean = true,
    val notifyOnExit: Boolean = true,
    val lastEvent: String = "none",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SosIncident(
    @DocumentId val id: String = "",
    val userId: String = "",
    val triggerType: String = "one_touch_sos",
    val status: String = "active",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val addressLabel: String = "",
    val sirenTriggered: Boolean = false,
    val batteryPercent: Int = 100,
    val notes: String = "",
    val evidenceCount: Int = 0,
    val shareCode: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val resolvedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class LocationBreadcrumb(
    @DocumentId val id: String = "",
    val userId: String = "",
    val incidentId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedMps: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val batteryPercent: Int = 100,
    @ServerTimestamp val recordedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class EvidenceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val incidentId: String = "general",
    val captureMode: String = "text_note",
    val title: String = "",
    val summary: String = "",
    val localEncryptedUri: String = "",
    val storagePath: String = "",
    val sha256Digest: String = "",
    val encryptionAlgo: String = "AES-256-GCM",
    val consentConfirmed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class LiveShareBeacon(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerAlias: String = "",
    val incidentId: String = "",
    val isActive: Boolean = true,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val batteryPercent: Int = 100,
    val statusMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DangerZone(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reporterAlias: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "poor_lighting",
    val severity: String = "medium",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 150.0,
    val verificationStatus: String = "pending",
    val confirmCount: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SafePlace(
    @DocumentId val id: String = "",
    val name: String = "",
    val category: String = "police_station",
    val address: String = "",
    val phone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isOpen24Hours: Boolean = true,
    val verifiedByAdmin: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SosGlobalAlert(
    @DocumentId val id: String = "",
    val incidentId: String = "",
    val userId: String = "",
    val userAlias: String = "",
    val triggerType: String = "one_touch_sos",
    val status: String = "active",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val batteryPercent: Int = 100,
    val sirenTriggered: Boolean = false,
    val shareId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class LocationSnapshot(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val speedMps: Float = 0f,
    val provider: String = "gps",
    val timestampMillis: Long = System.currentTimeMillis(),
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

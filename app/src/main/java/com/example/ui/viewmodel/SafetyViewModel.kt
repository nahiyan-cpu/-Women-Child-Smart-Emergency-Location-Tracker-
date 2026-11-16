package com.example.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DangerZone
import com.example.data.model.EmergencyContact
import com.example.data.model.EvidenceRecord
import com.example.data.model.LiveShareBeacon
import com.example.data.model.LocationSnapshot
import com.example.data.model.SafePlace
import com.example.data.model.SafetyGeofence
import com.example.data.model.SosGlobalAlert
import com.example.data.model.SosIncident
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.remote.BackendDispatchClient
import com.example.data.remote.SosDispatchRequest
import com.example.data.remote.toUserFriendlyFirestoreMessage
import com.example.data.repository.SafetyRepository
import com.example.security.CryptoVaultManager
import com.example.service.LocationAndGeofenceManager
import com.example.service.SirenPlayer
import com.example.service.SosLocationService
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SafetyViewModel(
    private val appContext: Context,
    private val repository: SafetyRepository,
    private val locationManager: LocationAndGeofenceManager,
    private val sirenPlayer: SirenPlayer,
    private val cryptoVault: CryptoVaultManager,
    private val backendClient: BackendDispatchClient,
    val currentUserId: String,
    val currentUserEmail: String?,
    val currentUserName: String?,
) : ViewModel() {

    val isAdminUser: Boolean =
        currentUserEmail.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)

    private val _currentLocation = MutableStateFlow<LocationSnapshot?>(null)
    val currentLocation: StateFlow<LocationSnapshot?> = _currentLocation.asStateFlow()

    private val _locationPermissionGranted = MutableStateFlow(locationManager.hasLocationPermission())
    val locationPermissionGranted: StateFlow<Boolean> = _locationPermissionGranted.asStateFlow()

    private val _isOffline = MutableStateFlow(!checkNetworkOnline())
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    val isSirenPlaying: StateFlow<Boolean> = sirenPlayer.isPlaying

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isSosTriggering = MutableStateFlow(false)
    val isSosTriggering: StateFlow<Boolean> = _isSosTriggering.asStateFlow()

    private val _trackedBeacon = MutableStateFlow<LiveShareBeacon?>(null)
    val trackedBeacon: StateFlow<LiveShareBeacon?> = _trackedBeacon.asStateFlow()

    private val _beaconLookupError = MutableStateFlow<String?>(null)
    val beaconLookupError: StateFlow<String?> = _beaconLookupError.asStateFlow()

    private var beaconObserveJob: Job? = null

    val profileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiState("Failed to load profile")

    val contactsState: StateFlow<UiState<List<EmergencyContact>>> =
        repository.observeEmergencyContacts(currentUserId)
            .toUiState("Failed to load emergency contacts")

    val geofencesState: StateFlow<UiState<List<SafetyGeofence>>> =
        repository.observeGeofences(currentUserId)
            .toUiState("Failed to load geofences")

    val incidentsState: StateFlow<UiState<List<SosIncident>>> =
        repository.observeSosIncidents(currentUserId)
            .toUiState("Failed to load incident history")

    val evidenceState: StateFlow<UiState<List<EvidenceRecord>>> =
        repository.observeEvidenceRecords(currentUserId)
            .toUiState("Failed to load evidence vault")

    val verifiedDangerZonesState: StateFlow<UiState<List<DangerZone>>> =
        repository.observeVerifiedDangerZones(currentUserId)
            .toUiState("Failed to load danger zones")

    val myDangerZonesState: StateFlow<UiState<List<DangerZone>>> =
        repository.observeMyDangerZones(currentUserId)
            .toUiState("Failed to load your danger reports")

    val safePlacesState: StateFlow<UiState<List<SafePlace>>> =
        repository.observeVerifiedSafePlaces(currentUserId)
            .toUiState("Failed to load safe places")

    val adminSosAlertsState: StateFlow<UiState<List<SosGlobalAlert>>> =
        if (isAdminUser) {
            repository.observeGlobalSosAlertsAdmin(currentUserId)
                .toUiState("Failed to load SOS dispatch feed")
        } else {
            MutableStateFlow(UiState.Success(emptyList()))
        }

    val adminDangerZonesState: StateFlow<UiState<List<DangerZone>>> =
        if (isAdminUser) {
            repository.observeAllDangerZonesAdmin(currentUserId)
                .toUiState("Failed to load danger zone moderation queue")
        } else {
            MutableStateFlow(UiState.Success(emptyList()))
        }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        registerConnectivityObserver()
        viewModelScope.launch {
            repository.ensureUserProfile(
                displayName = currentUserName ?: "SafeGuard User",
                email = currentUserEmail,
            )
            syncFcmTokenSafely()
            if (locationManager.hasLocationPermission()) {
                refreshLocation()
            }
        }
    }

    private fun <T> Flow<T>.toUiState(fallbackError: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackError, rootError)
                emit(UiState.Error(rootError.toUserFriendlyFirestoreMessage(fallbackError)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading,
            )

    fun onLocationPermissionResult(granted: Boolean) {
        _locationPermissionGranted.value = granted || locationManager.hasLocationPermission()
        if (_locationPermissionGranted.value) {
            refreshLocation()
        }
    }

    fun refreshLocation() {
        viewModelScope.launch {
            if (!locationManager.hasLocationPermission()) {
                _locationPermissionGranted.value = false
                return@launch
            }
            _locationPermissionGranted.value = true
            locationManager.getCurrentLocation()
                .onSuccess { _currentLocation.value = it }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Unable to read GPS location."
                }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun toggleSiren() {
        if (sirenPlayer.isPlaying.value) {
            sirenPlayer.stop()
        } else {
            sirenPlayer.start()
        }
    }

    fun triggerOneTouchSos(notes: String = "", startSirenIfConfigured: Boolean = true) {
        if (_isSosTriggering.value) return
        viewModelScope.launch {
            _isSosTriggering.value = true
            try {
                val profile = (profileState.value as? UiState.Success)?.data
                val autoSiren = startSirenIfConfigured && (profile?.sosAutoSiren ?: true)
                val loc = if (locationManager.hasLocationPermission()) {
                    locationManager.getCurrentLocation().getOrNull() ?: _currentLocation.value
                } else {
                    _currentLocation.value
                } ?: LocationSnapshot(0.0, 0.0, 0f, 0f, "unavailable")

                val addressSummary = if (loc.provider == "unavailable") {
                    "GPS permission denied or unavailable"
                } else {
                    String.format("%.5f, %.5f (±%.0fm)", loc.latitude, loc.longitude, loc.accuracyMeters)
                }

                val battery = locationManager.getBatteryPercent()
                val alias = (profile?.displayName ?: currentUserName ?: "SafeGuard Member")
                    .substringBefore(" ")
                    .ifBlank { "Member" }

                val result = repository.triggerSosIncident(
                    userAlias = alias,
                    triggerType = "one_touch_sos",
                    location = loc,
                    addressLabel = addressSummary,
                    sirenTriggered = autoSiren,
                    batteryPercent = battery,
                    notes = notes.ifBlank { "Emergency SOS triggered from SafeGuard app" },
                )

                result.onSuccess { incident ->
                    if (autoSiren) {
                        sirenPlayer.start()
                    }
                    if (locationManager.hasLocationPermission()) {
                        runCatching {
                            SosLocationService.start(
                                context = appContext,
                                incidentId = incident.id,
                                shareId = incident.shareCode,
                                sirenActive = autoSiren,
                            )
                        }
                    }
                    val fcmResult = backendClient.dispatchSosViaBackend(
                        SosDispatchRequest(
                            incidentId = incident.id,
                            shareId = incident.shareCode,
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            triggerType = "one_touch_sos",
                            message = "$alias triggered SafeGuard SOS at $addressSummary",
                        )
                    )
                    _statusBanner.value = if (fcmResult.isSuccess) {
                        "SOS Active & Push Dispatch Sent. Share ID: ${incident.shareCode}"
                    } else {
                        "SOS Active (Firestore & Live Beacon ${incident.shareCode}). Use SMS/Call shortcuts to notify contacts directly."
                    }
                }.onFailure { err ->
                    _statusBanner.value = err.toUserFriendlyFirestoreMessage("Failed to trigger SOS")
                }
            } finally {
                _isSosTriggering.value = false
            }
        }
    }

    fun resolveSosIncident(
        incidentId: String,
        shareId: String,
        finalStatus: String = "resolved",
        enteredPin: String? = null,
    ): Boolean {
        val profile = (profileState.value as? UiState.Success)?.data
        val savedHash = profile?.emergencyPinHash
        if (!savedHash.isNullOrBlank()) {
            if (enteredPin.isNullOrBlank() || CryptoVaultManager.hashPin(enteredPin) != savedHash) {
                _statusBanner.value = "Incorrect Emergency PIN. Incident remains active."
                return false
            }
        }
        sirenPlayer.stop()
        runCatching { SosLocationService.stop(appContext) }
        viewModelScope.launch {
            repository.resolveSosIncident(incidentId, shareId, finalStatus)
                .onSuccess {
                    _statusBanner.value = if (finalStatus == "false_alarm") {
                        "SOS marked as false alarm. Live tracking stopped."
                    } else {
                        "SOS resolved. Stay safe!"
                    }
                }
                .onFailure { err ->
                    _statusBanner.value = err.toUserFriendlyFirestoreMessage("Failed to resolve SOS")
                }
        }
        return true
    }

    fun saveProfile(
        displayName: String,
        phone: String,
        bloodType: String,
        medicalNotes: String,
        homeAddress: String,
        newPin: String,
        sosAutoSiren: Boolean,
        locationSharingEnabled: Boolean,
    ) {
        viewModelScope.launch {
            val existing = (profileState.value as? UiState.Success)?.data
            val pinHash = if (newPin.isNotBlank()) CryptoVaultManager.hashPin(newPin) else existing?.emergencyPinHash
            val updated = UserProfile(
                id = currentUserId,
                userId = currentUserId,
                displayName = displayName.trim().ifBlank { "SafeGuard User" },
                email = currentUserEmail,
                phone = phone.trim(),
                bloodType = bloodType.trim(),
                medicalNotes = medicalNotes.trim(),
                homeAddress = homeAddress.trim(),
                emergencyPinHash = pinHash,
                sosAutoSiren = sosAutoSiren,
                locationSharingEnabled = locationSharingEnabled,
                fcmToken = existing?.fcmToken,
            )
            repository.saveUserProfile(updated)
                .onSuccess { _statusBanner.value = "Safety profile saved." }
                .onFailure { _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to save profile") }
        }
    }

    fun addOrUpdateContact(contact: EmergencyContact) {
        viewModelScope.launch {
            val res = if (contact.id.isBlank()) {
                repository.addEmergencyContact(contact)
            } else {
                repository.updateEmergencyContact(contact)
            }
            res.onSuccess { _statusBanner.value = "Emergency contact saved." }
                .onFailure { _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to save contact") }
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            repository.deleteEmergencyContact(contactId)
                .onSuccess { _statusBanner.value = "Emergency contact removed." }
                .onFailure { _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to delete contact") }
        }
    }

    fun addGeofence(
        name: String,
        zoneType: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        notifyOnEnter: Boolean,
        notifyOnExit: Boolean,
    ) {
        viewModelScope.launch {
            repository.addGeofence(
                SafetyGeofence(
                    userId = currentUserId,
                    name = name,
                    zoneType = zoneType,
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters,
                    isActive = true,
                    notifyOnEnter = notifyOnEnter,
                    notifyOnExit = notifyOnExit,
                )
            ).onSuccess {
                syncGeofencesToOs()
                _statusBanner.value = "Geofence '$name' created."
            }.onFailure {
                _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to create geofence")
            }
        }
    }

    fun toggleGeofence(geofence: SafetyGeofence) {
        viewModelScope.launch {
            repository.updateGeofenceStatus(geofence.id, isActive = !geofence.isActive)
                .onSuccess { syncGeofencesToOs() }
                .onFailure { _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to update geofence") }
        }
    }

    fun deleteGeofence(geofenceId: String) {
        viewModelScope.launch {
            repository.deleteGeofence(geofenceId)
                .onSuccess { syncGeofencesToOs() }
                .onFailure { _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to delete geofence") }
        }
    }

    private fun syncGeofencesToOs() {
        viewModelScope.launch {
            val zones = (geofencesState.value as? UiState.Success)?.data.orEmpty()
            if (locationManager.hasFineLocationPermission()) {
                locationManager.syncGeofencesWithOs(zones)
            }
        }
    }

    fun saveConsentedTextEvidence(
        incidentId: String,
        title: String,
        noteText: String,
        consentConfirmed: Boolean,
        uploadToCloud: Boolean,
    ) {
        if (!consentConfirmed) {
            _statusBanner.value = "Explicit consent is required before saving evidence."
            return
        }
        viewModelScope.launch {
            val encrypted = cryptoVault.encryptAndStoreBytes(
                rawBytes = noteText.toByteArray(Charsets.UTF_8),
                extension = "txt",
                userId = currentUserId,
                uploadToCloudStorage = uploadToCloud,
            )
            encrypted.onSuccess { artifact ->
                repository.addEvidenceRecord(
                    EvidenceRecord(
                        userId = currentUserId,
                        incidentId = incidentId.ifBlank { "general" },
                        captureMode = "text_note",
                        title = title,
                        summary = noteText.take(200),
                        localEncryptedUri = artifact.localVaultPath,
                        storagePath = artifact.storagePath,
                        sha256Digest = artifact.sha256Digest,
                        encryptionAlgo = artifact.encryptionAlgo,
                        consentConfirmed = true,
                    )
                ).onSuccess {
                    _statusBanner.value = "Evidence encrypted (AES-256-GCM) & logged."
                }.onFailure {
                    _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to record evidence")
                }
            }.onFailure {
                _statusBanner.value = "Encryption failed: ${it.localizedMessage}"
            }
        }
    }

    fun saveConsentedMediaEvidence(
        uri: Uri,
        captureMode: String,
        extension: String,
        incidentId: String,
        title: String,
        summary: String,
        consentConfirmed: Boolean,
        uploadToCloud: Boolean,
    ) {
        if (!consentConfirmed) {
            _statusBanner.value = "Explicit consent is required before storing media evidence."
            return
        }
        viewModelScope.launch {
            val encrypted = cryptoVault.encryptAndStoreUri(
                sourceUri = uri,
                extension = extension,
                userId = currentUserId,
                uploadToCloudStorage = uploadToCloud,
            )
            encrypted.onSuccess { artifact ->
                repository.addEvidenceRecord(
                    EvidenceRecord(
                        userId = currentUserId,
                        incidentId = incidentId.ifBlank { "general" },
                        captureMode = captureMode,
                        title = title,
                        summary = summary,
                        localEncryptedUri = artifact.localVaultPath,
                        storagePath = artifact.storagePath,
                        sha256Digest = artifact.sha256Digest,
                        encryptionAlgo = artifact.encryptionAlgo,
                        consentConfirmed = true,
                    )
                ).onSuccess {
                    _statusBanner.value = "Media encrypted with AES-256-GCM & saved to vault."
                }.onFailure {
                    _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to save evidence record")
                }
            }.onFailure {
                _statusBanner.value = "Unable to encrypt media: ${it.localizedMessage}"
            }
        }
    }

    fun saveConsentedBytesEvidence(
        rawBytes: ByteArray,
        captureMode: String,
        extension: String,
        incidentId: String,
        title: String,
        summary: String,
        consentConfirmed: Boolean,
        uploadToCloud: Boolean,
    ) {
        if (!consentConfirmed) return
        viewModelScope.launch {
            cryptoVault.encryptAndStoreBytes(rawBytes, extension, currentUserId, uploadToCloud)
                .onSuccess { artifact ->
                    repository.addEvidenceRecord(
                        EvidenceRecord(
                            userId = currentUserId,
                            incidentId = incidentId.ifBlank { "general" },
                            captureMode = captureMode,
                            title = title,
                            summary = summary,
                            localEncryptedUri = artifact.localVaultPath,
                            storagePath = artifact.storagePath,
                            sha256Digest = artifact.sha256Digest,
                            encryptionAlgo = artifact.encryptionAlgo,
                            consentConfirmed = true,
                        )
                    ).onSuccess {
                        _statusBanner.value = "Consented $captureMode encrypted & archived."
                    }.onFailure {
                        _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to save evidence metadata")
                    }
                }.onFailure {
                    _statusBanner.value = "Encryption failed: ${it.localizedMessage}"
                }
        }
    }

    fun deleteEvidence(evidenceId: String) {
        viewModelScope.launch {
            repository.deleteEvidenceRecord(evidenceId)
                .onSuccess { _statusBanner.value = "Evidence entry removed." }
                .onFailure { _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to remove evidence") }
        }
    }

    suspend fun decryptNotePreview(vaultPath: String): Result<String> {
        return cryptoVault.decryptVaultFile(vaultPath).map { it.toString(Charsets.UTF_8) }
    }

    fun reportDangerZone(
        title: String,
        description: String,
        category: String,
        severity: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ) {
        viewModelScope.launch {
            val alias = (currentUserName ?: "Community Member").substringBefore(" ").ifBlank { "Member" }
            repository.reportDangerZone(
                reporterAlias = alias,
                title = title,
                description = description,
                category = category,
                severity = severity,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
            ).onSuccess {
                _statusBanner.value = "Danger zone submitted for admin verification."
            }.onFailure {
                _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to submit danger report")
            }
        }
    }

    fun confirmDangerZone(zoneId: String) {
        viewModelScope.launch {
            repository.confirmDangerZone(zoneId)
                .onSuccess { _statusBanner.value = "Danger zone confirmation recorded." }
                .onFailure { _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to confirm danger zone") }
        }
    }

    fun observeLiveShareById(shareId: String) {
        val trimmed = shareId.trim()
        if (trimmed.isBlank()) {
            _beaconLookupError.value = "Enter a valid Share ID (e.g. shr_...)"
            return
        }
        _beaconLookupError.value = null
        beaconObserveJob?.cancel()
        beaconObserveJob = viewModelScope.launch {
            repository.observeLiveShareBeacon(trimmed)
                .catch { err ->
                    _beaconLookupError.value = err.toUserFriendlyFirestoreMessage("Beacon not found")
                    _trackedBeacon.value = null
                }
                .collect { beacon ->
                    if (beacon == null) {
                        _beaconLookupError.value = "No beacon found for ID '$trimmed'"
                    } else {
                        _beaconLookupError.value = null
                    }
                    _trackedBeacon.value = beacon
                }
        }
    }

    fun clearTrackedBeacon() {
        beaconObserveJob?.cancel()
        _trackedBeacon.value = null
        _beaconLookupError.value = null
    }

    // --- Admin Controls ---

    fun moderateDangerZoneAdmin(zoneId: String, status: String) {
        viewModelScope.launch {
            repository.moderateDangerZoneAdmin(zoneId, status)
                .onSuccess { _statusBanner.value = "Zone marked as $status." }
                .onFailure { _statusBanner.value = it.toUserFriendlyFirestoreMessage("Admin moderation failed") }
        }
    }

    fun updateGlobalSosStatusAdmin(alert: SosGlobalAlert, newStatus: String) {
        viewModelScope.launch {
            repository.updateGlobalSosStatusAdmin(alert.id, alert.userId, alert.incidentId, newStatus)
                .onSuccess { _statusBanner.value = "SOS alert updated to $newStatus." }
                .onFailure { _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to update SOS status") }
        }
    }

    fun addSafePlaceAdmin(
        name: String,
        category: String,
        address: String,
        phone: String,
        latitude: Double,
        longitude: Double,
        isOpen24Hours: Boolean,
    ) {
        viewModelScope.launch {
            repository.addSafePlaceAdmin(
                SafePlace(
                    name = name,
                    category = category,
                    address = address,
                    phone = phone,
                    latitude = latitude,
                    longitude = longitude,
                    isOpen24Hours = isOpen24Hours,
                    verifiedByAdmin = true,
                    createdBy = currentUserId,
                )
            ).onSuccess {
                _statusBanner.value = "Verified safe place '$name' published."
            }.onFailure {
                _statusBanner.value = it.toUserFriendlyFirestoreMessage("Failed to add safe place")
            }
        }
    }

    private suspend fun syncFcmTokenSafely() {
        runCatching {
            val token = Firebase.messaging.token.await()
            if (token.isNotBlank()) {
                repository.updateFcmToken(token)
            }
        }
    }

    private fun checkNetworkOnline(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun registerConnectivityObserver() {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOffline.value = false
                }
                override fun onLost(network: Network) {
                    _isOffline.value = !checkNetworkOnline()
                }
            }
            networkCallback = callback
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        sirenPlayer.stop()
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback?.let { cm?.unregisterNetworkCallback(it) }
        }
    }

    companion object {
        private const val TAG = "SafetyViewModel"
        const val BOOTSTRAP_ADMIN_EMAIL = "nahiyanctg07@gmail.com"
    }
}

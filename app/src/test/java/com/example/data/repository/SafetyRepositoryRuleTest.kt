package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.EmergencyContact
import com.example.data.model.EvidenceRecord
import com.example.data.model.LocationSnapshot
import com.example.data.model.SafetyGeofence
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class SafetyRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun emergencyContact_ownerCrud_succeedsAndEmitsViaFlow() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = SafetyRepository(firestore, auth)

        val contactId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addEmergencyContact(
                EmergencyContact(
                    name = "Sarah Guardian",
                    relationship = "Mother",
                    phone = "+15550192834",
                    isPrimary = true,
                    notifyOnSos = true,
                    notifyOnGeofence = true,
                )
            ).getOrThrow()
        }
        assertTrue(contactId.isNotBlank())

        val contacts = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeEmergencyContacts(uid).first { list -> list.any { it.id == contactId } }
        }
        assertEquals("Sarah Guardian", contacts.first { it.id == contactId }.name)
    }

    @Test
    fun sosIncident_triggerAndResolve_persistsAcrossFlows() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = SafetyRepository(firestore, auth)

        val incident = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.triggerSosIncident(
                userAlias = "Alice",
                triggerType = "one_touch_sos",
                location = LocationSnapshot(37.7749, -122.4194, 8f, 0f),
                addressLabel = "Market St, SF",
                sirenTriggered = true,
                batteryPercent = 92,
                notes = "Unit test SOS",
            ).getOrThrow()
        }

        val incidents = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSosIncidents(uid).first { list -> list.any { it.id == incident.id } }
        }
        assertEquals("active", incidents.first { it.id == incident.id }.status)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.resolveSosIncident(incident.id, incident.shareCode, "resolved", "Safe now").getOrThrow()
        }

        val updated = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSosIncidents(uid).first { list ->
                list.any { it.id == incident.id && it.status == "resolved" }
            }
        }
        assertEquals("resolved", updated.first { it.id == incident.id }.status)
    }

    @Test
    fun geofenceAndEvidence_withConsent_succeed() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = SafetyRepository(firestore, auth)

        val geoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addGeofence(
                SafetyGeofence(
                    name = "Campus Safe Zone ${UUID.randomUUID().toString().take(4)}",
                    zoneType = "safe_zone",
                    latitude = 37.8719,
                    longitude = -122.2585,
                    radiusMeters = 300.0,
                )
            ).getOrThrow()
        }
        assertTrue(geoId.isNotBlank())

        val evId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addEvidenceRecord(
                EvidenceRecord(
                    incidentId = "general",
                    captureMode = "text_note",
                    title = "Observation Note",
                    summary = "Recorded with explicit user consent.",
                    localEncryptedUri = "vault/test_note.enc",
                    storagePath = "",
                    sha256Digest = "abcdef0123456789abcdef0123456789",
                    encryptionAlgo = "AES-256-GCM",
                    consentConfirmed = true,
                )
            ).getOrThrow()
        }
        assertTrue(evId.isNotBlank())

        val evidenceList = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeEvidenceRecords(uid).first { list -> list.any { it.id == evId } }
        }
        assertTrue(evidenceList.any { it.id == evId && it.consentConfirmed })
    }

    @Test
    fun crossUserAccess_readingAlicesContactsAsBob_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SafetyRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addEmergencyContact(
                EmergencyContact(name = "Private Contact", relationship = "Sister", phone = "+15551112222")
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = SafetyRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeEmergencyContacts(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user contact read")
        } catch (e: Exception) {
            val firestoreEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    @Test
    fun unauthenticatedCaller_observingDangerZones_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = SafetyRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeVerifiedDangerZones("unauth_user").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val firestoreEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_safeguard@test.com"
        const val BOB_EMAIL = "bob_safeguard@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}

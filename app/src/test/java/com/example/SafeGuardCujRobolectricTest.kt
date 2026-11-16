package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.BackendDispatchClient
import com.example.data.repository.SafetyRepository
import com.example.security.CryptoVaultManager
import com.example.service.LocationAndGeofenceManager
import com.example.service.SirenPlayer
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.ContactsAndProfileScreen
import com.example.ui.screens.HistoryAndEvidenceScreen
import com.example.ui.screens.HomeSosScreen
import com.example.ui.screens.SafetyMapScreen
import com.example.ui.theme.SafeGuardTheme
import com.example.ui.viewmodel.SafetyViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class SafeGuardCujRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val databaseId = context.getString(R.string.firestore_database_id)
        val defaultApp = FirebaseApp.getApps(context)
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.safeguard.wctkxr")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key-for-emulator")
                    .build(),
            ).also { created ->
                val fs = FirebaseFirestore.getInstance(created, databaseId)
                fs.useEmulator("127.0.0.1", 8085)
                fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
                FirebaseAuth.getInstance(created).useEmulator("127.0.0.1", 9099)
            }
        firestore = FirebaseFirestore.getInstance(defaultApp, databaseId)
        auth = FirebaseAuth.getInstance(defaultApp)
    }

    private fun buildViewModel(
        userId: String = "uid_cuj_user",
        email: String = "alice@test.com",
        name: String = "Alice Guardian",
    ): SafetyViewModel {
        return SafetyViewModel(
            appContext = context,
            repository = SafetyRepository(firestore, auth),
            locationManager = LocationAndGeofenceManager(context),
            sirenPlayer = SirenPlayer(context),
            cryptoVault = CryptoVaultManager(context),
            backendClient = BackendDispatchClient(),
            currentUserId = userId,
            currentUserEmail = email,
            currentUserName = name,
        )
    }

    @Test
    fun authScreen_displaysBrandingAndGoogleSignInButton() {
        composeRule.setContent {
            SafeGuardTheme {
                AuthScreen(onAuthSuccess = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SafeGuard SOS").assertIsDisplayed()
        composeRule.onNodeWithText("One-Touch Protection & Live Beacon").assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeSosScreen_rendersCoreControlsAndTogglesSiren() {
        val vm = buildViewModel()
        composeRule.setContent {
            SafeGuardTheme {
                HomeSosScreen(
                    viewModel = vm,
                    onRequestLocationPermission = {},
                    onNavigateToContacts = {},
                    onNavigateToEvidence = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sos_hero_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("siren_toggle_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Loud Siren").assertIsDisplayed()
        composeRule.onNodeWithTag("call_emergency_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("track_contact_beacon_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("quick_evidence_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("siren_toggle_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Stop Siren").assertIsDisplayed()

        composeRule.onNodeWithTag("siren_toggle_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Loud Siren").assertIsDisplayed()
    }

    @Test
    fun safetyMapScreen_rendersRadarSwitchesFiltersAndOpensGeofenceDialog() {
        val vm = buildViewModel()
        composeRule.setContent {
            SafeGuardTheme {
                SafetyMapScreen(
                    viewModel = vm,
                    onRequestLocationPermission = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_geofence_button").assertIsDisplayed()
        composeRule.onNodeWithTag("report_danger_zone_button").assertIsDisplayed()

        composeRule.onNodeWithText("Danger Zones").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("My Geofences").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Safe Places").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("add_geofence_button").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100)
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("Expected Geofence AlertDialog to be created", dialog)
        assertTrue("Expected Geofence AlertDialog to be showing", dialog.isShowing)
        dialog.dismiss()
    }

    @Test
    fun contactsAndProfileScreen_displaysProfileControlsAndOpensAddContactDialog() {
        val vm = buildViewModel()
        composeRule.setContent {
            SafeGuardTheme {
                ContactsAndProfileScreen(
                    viewModel = vm,
                    onSignOutClick = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_contact_button").assertIsDisplayed()
        composeRule.onNodeWithTag("save_profile_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sign_out_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("add_contact_button").performScrollTo()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("add_contact_button").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100)
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("Expected Add Contact AlertDialog to be created", dialog)
        assertTrue("Expected Add Contact AlertDialog to be showing", dialog.isShowing)
        dialog.dismiss()
    }

    @Test
    fun historyAndEvidenceScreen_requiresExplicitConsentBeforeCaptureProceed() {
        val vm = buildViewModel()
        composeRule.setContent {
            SafeGuardTheme {
                HistoryAndEvidenceScreen(viewModel = vm, initialTab = 1)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("capture_evidence_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Consent-Based Evidence Capture").assertIsDisplayed()
        composeRule.onNodeWithText("Proceed").assertIsNotEnabled()

        composeRule.onNodeWithText("I explicitly consent to capture and encrypt this evidence.").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Proceed").assertIsEnabled()
    }

    @Test
    fun adminDashboardScreen_enforcesAdminRoleAndDisplaysAdminQueueForVerifiedAdmin() {
        val regularVm = buildViewModel(email = "regular@test.com", name = "Regular User")
        composeRule.setContent {
            SafeGuardTheme {
                AdminDashboardScreen(viewModel = regularVm)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Admin Role Required").assertIsDisplayed()
    }

    @Test
    fun adminDashboardScreen_rendersAdminControlsForBootstrapAdmin() {
        val adminVm = buildViewModel(
            userId = "uid_admin",
            email = SafetyViewModel.BOOTSTRAP_ADMIN_EMAIL,
            name = "Admin Responder",
        )
        composeRule.setContent {
            SafeGuardTheme {
                AdminDashboardScreen(viewModel = adminVm)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Admin Dispatch & Moderation").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_add_safe_place_button").assertIsDisplayed()
        composeRule.onNodeWithText("Live SOS Dispatch Feed").assertIsDisplayed()
        composeRule.onNodeWithText("Danger Zone Moderation Queue").assertIsDisplayed()
    }

    private companion object {
        const val TEST_APP_NAME = "cuj-test-app"
    }
}

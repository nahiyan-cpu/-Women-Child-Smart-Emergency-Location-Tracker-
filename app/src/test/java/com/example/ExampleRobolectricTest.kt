package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.CryptoVaultManager
import com.example.service.LocationAndGeofenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun readAppNameFromContext_returnsSafeGuardSos() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SafeGuard SOS", appName)
    }

    @Test
    fun cryptoVault_sha256AndPinHash_areDeterministicAndConsistent() {
        val hash1 = CryptoVaultManager.hashPin("4829")
        val hash2 = CryptoVaultManager.hashPin(" 4829 ")
        val hashOther = CryptoVaultManager.hashPin("9999")
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hashOther)
    }

    @Test
    fun distanceFormatting_formatsMetersAndKilometers() {
        assertEquals("450 m", LocationAndGeofenceManager.formatDistance(450f))
        assertTrue(LocationAndGeofenceManager.formatDistance(1500f).contains("km"))
    }
}

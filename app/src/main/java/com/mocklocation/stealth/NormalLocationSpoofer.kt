package com.mocklocation.stealth

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import java.lang.reflect.Field

class NormalLocationSpoofer(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        "fused"
    )

    fun startSpoofing(lat: Double, lng: Double) {
        setupProviders()
        pushLocation(LocationManager.GPS_PROVIDER, lat, lng)
        pushLocation(LocationManager.NETWORK_PROVIDER, lat, lng)
        pushLocation("fused", lat, lng)
    }

    private fun setupProviders() {
        providers.forEach { provider ->
            try {
                try { locationManager.removeTestProvider(provider) } catch (_: Exception) {}
                locationManager.addTestProvider(
                    provider,
                    false, false, false, false,
                    true, true, true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(provider, true)
            } catch (e: Exception) {
                Log.w("NormalSpoofer", "Provider $provider: ${e.message}")
            }
        }
    }

    fun pushLocation(provider: String, lat: Double, lng: Double) {
        try {
            val loc = Location(provider).apply {
                latitude  = lat  + (Math.random() - 0.5) * 0.000004
                longitude = lng  + (Math.random() - 0.5) * 0.000004
                altitude  = 10.0 + Math.random() * 8
                accuracy  = 2.0f + (Math.random() * 2).toFloat()
                bearing   = (Math.random() * 360).toFloat()
                speed     = (0.2 + Math.random() * 0.5).toFloat()
                time      = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                val b = android.os.Bundle()
                b.putInt("satellites", 7 + (Math.random() * 3).toInt())
                extras = b
            }
            // Clear isMock flag — same as root version
            clearMockFlag(loc)
            locationManager.setTestProviderLocation(provider, loc)
        } catch (e: Exception) {
            Log.w("NormalSpoofer", "Push failed $provider: ${e.message}")
        }
    }

    private fun clearMockFlag(location: Location) {
        try {
            val field: Field = Location::class.java.getDeclaredField("mIsFromMockProvider")
            field.isAccessible = true
            field.setBoolean(location, false)
        } catch (e1: Exception) {
            try {
                val field: Field = Location::class.java.getDeclaredField("mMock")
                field.isAccessible = true
                field.setBoolean(location, false)
            } catch (e2: Exception) {
                try {
                    Location::class.java.declaredFields
                        .filter { it.type == Boolean::class.javaPrimitiveType }
                        .filter { it.name.lowercase().contains("mock") }
                        .forEach { field ->
                            field.isAccessible = true
                            field.setBoolean(location, false)
                        }
                } catch (_: Exception) {}
            }
        }
    }

    // ── Check if mock location permission is granted ──────────────
    fun isMockPermissionGranted(): Boolean {
        return try {
            locationManager.addTestProvider(
                "test_check", false, false, false, false,
                true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            )
            locationManager.removeTestProvider("test_check")
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun stopSpoofing() {
        providers.forEach { provider ->
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {}
        }
    }
}

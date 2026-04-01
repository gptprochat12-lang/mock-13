package com.mocklocation.stealth

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.lang.reflect.Field

class NormalLocationSpoofer(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val isXiaomiFamily: Boolean by lazy {
        val brand = Build.BRAND.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")
    }

    private val providers: List<String>
        get() = if (isXiaomiFamily) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused")
        }

    private var jitterLat = 0.0
    private var jitterLng = 0.0
    private var jitterAlt = 0.0

    fun startSpoofing(lat: Double, lng: Double) {
        jitterLat = (Math.random() - 0.5) * 0.000003
        jitterLng = (Math.random() - 0.5) * 0.000003
        jitterAlt = Math.random() * 8
        setupProviders()
        providers.forEach { pushLocation(it, lat, lng) }
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
                latitude = lat + jitterLat
                longitude = lng + jitterLng
                altitude = 10.0 + jitterAlt
                accuracy = if (provider == LocationManager.NETWORK_PROVIDER) 8.0f else 3.5f
                bearing = 0f
                speed = 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                val b = android.os.Bundle()
                b.putInt("satellites", 8)
                extras = b
            }
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

    fun activeProviders(): List<String> = providers

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

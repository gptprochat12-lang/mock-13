package com.mocklocation.stealth

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import java.io.DataOutputStream

class RootLocationSpoofer(private val context: Context) {

    companion object { const val TAG = "RootSpoofer" }

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val providers = mutableListOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )

    // Jitter calculated ONCE at start — stable position, not robotic
    private var jitterLat = 0.0
    private var jitterLng = 0.0
    private var jitterAlt = 0.0

    // Xiaomi/MIUI note:
    // Do NOT force location_mode=1 (Battery Saving). On some Xiaomi devices
    // that effectively disables GPS hardware and causes stale/stuck fixes.

    fun startSpoofing(lat: Double, lng: Double): Boolean {
        return try {
            jitterLat = (Math.random() - 0.5) * 0.000003
            jitterLng = (Math.random() - 0.5) * 0.000003
            jitterAlt = Math.random() * 8

            execRootBatch(listOf(
                // Grant mock permission
                "appops set ${context.packageName} android:mock_location allow",
                "settings put secure mock_location_app ${context.packageName}",
                // Keep current location mode unchanged.
                // Clearing GMS can make fused location stall on MIUI, so avoid it.
                // Xiaomi-specific behavior is handled by preferring stable GPS/NETWORK mock providers.
            ))

            configureProvidersForDevice()
            setupProviders()
            pushLocation(lat, lng)
            Log.d(TAG, "Spoofing started — preserved device location mode ✅")
            true
        } catch (e: Exception) {
            Log.e(TAG, "startSpoofing failed: ${e.message}")
            false
        }
    }

    private fun configureProvidersForDevice() {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val brand = android.os.Build.BRAND.lowercase()

        // MIUI/HyperOS devices can behave badly when a test provider named "fused"
        // is injected. Keep to GPS + NETWORK there.
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
            manufacturer.contains("poco") || brand.contains("xiaomi") ||
            brand.contains("redmi") || brand.contains("poco")) {
            providers.remove("fused")
            Log.d(TAG, "Xiaomi-like device detected; skipping fused test provider")
        } else if (!providers.contains("fused")) {
            providers.add("fused")
        }
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
                Log.d(TAG, "Provider ready: $provider ✅")
            } catch (e: Exception) {
                Log.w(TAG, "Provider $provider: ${e.message}")
            }
        }
    }

    fun pushLocation(lat: Double, lng: Double) {
        providers.forEach { provider ->
            try {
                val loc = buildStealthLocation(provider, lat, lng)
                locationManager.setTestProviderLocation(provider, loc)
            } catch (e: Exception) {
                Log.w(TAG, "Push failed $provider: ${e.message}")
            }
        }
    }

    private fun buildStealthLocation(provider: String, lat: Double, lng: Double): Location {
        val loc = Location(provider)
        // Stable jitter — same every push, looks like real GPS on fixed spot
        loc.latitude  = lat + jitterLat
        loc.longitude = lng + jitterLng
        loc.altitude  = 10.0 + jitterAlt
        loc.accuracy  = 3.5f
        loc.bearing   = 0f
        loc.speed     = 0f
        loc.time      = System.currentTimeMillis()
        loc.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        val b = android.os.Bundle()
        b.putInt("satellites", 8)
        loc.extras = b
        clearMockFlag(loc)
        return loc
    }

    private fun clearMockFlag(location: Location) {
        val fieldNames = listOf(
            "mIsFromMockProvider", "mMock",
            "isFromMockProvider",  "isMock"
        )
        for (name in fieldNames) {
            try {
                val f = Location::class.java.getDeclaredField(name)
                f.isAccessible = true
                f.setBoolean(location, false)
                return
            } catch (_: Exception) {}
        }
        try {
            Location::class.java.declaredFields
                .filter { it.type == Boolean::class.javaPrimitiveType }
                .filter { it.name.lowercase().contains("mock") }
                .forEach { f -> f.isAccessible = true; f.setBoolean(location, false) }
        } catch (_: Exception) {}
    }

    fun injectLocation(lat: Double, lng: Double) = pushLocation(lat, lng)

    fun stopSpoofing() {
        providers.forEach { provider ->
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {}
        }
        execRootBatch(listOf(
            "settings put secure mock_location_app ''",
            "appops set ${context.packageName} android:mock_location default"
        ))
        Log.d(TAG, "Spoofing stopped ✅")
    }

    private fun execRootSingle(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
            val out = process.inputStream.bufferedReader().readText()
            os.close()
            out
        } catch (e: Exception) { "" }
    }

    private fun execRootBatch(commands: List<String>): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            commands.forEach { cmd -> os.writeBytes("$cmd\n") }
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
            val out = process.inputStream.bufferedReader().readText()
            os.close()
            out
        } catch (e: Exception) {
            Log.e(TAG, "execRootBatch: ${e.message}"); ""
        }
    }
}

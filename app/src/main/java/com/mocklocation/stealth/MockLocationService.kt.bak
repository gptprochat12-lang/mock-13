package com.mocklocation.stealth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.LocationManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MockLocationService : Service() {

    private lateinit var rootSpoofer:   RootLocationSpoofer
    private lateinit var normalSpoofer: NormalLocationSpoofer
    private var usingRoot = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        var isRunning    = false
        var currentLat   = 0.0
        var currentLng   = 0.0
        var isRootMode   = false
        const val CHANNEL_ID        = "loc_svc_v2"
        const val NOTIF_ID          = 101
        const val EXTRA_LAT         = "latitude"
        const val EXTRA_LNG         = "longitude"
        const val ACTION_STOP       = "ACTION_STOP"
        // Push every 200ms to fill gaps — prevents real GPS slipping through
        // BUT coordinates stay stable (jitter calculated once, not per push)
        // Display in UI updates every 1 second — looks natural
        const val PUSH_INTERVAL_MS    = 200L
        const val DISPLAY_INTERVAL_MS = 1000L
    }

    override fun onCreate() {
        super.onCreate()
        rootSpoofer   = RootLocationSpoofer(this)
        normalSpoofer = NormalLocationSpoofer(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        val lat = intent?.getDoubleExtra(EXTRA_LAT, 0.0) ?: return START_NOT_STICKY
        val lng = intent?.getDoubleExtra(EXTRA_LNG, 0.0) ?: return START_NOT_STICKY

        currentLat = lat
        currentLng = lng

        scope.launch {
            usingRoot  = RootChecker.isSuAvailable()
            isRootMode = usingRoot

            startForeground(NOTIF_ID, buildNotification(lat, lng))
            isRunning = true

            if (usingRoot) {
                // NO GPS disable — GPS stays ON ✅
                // Mock provider takes priority naturally
                rootSpoofer.startSpoofing(lat, lng)
            } else {
                normalSpoofer.startSpoofing(lat, lng)
            }

            var pushCount = 0L

            // Push every 200ms — fills ALL gaps so real GPS never slips through
            // Same coordinates every push — not robotic, looks like stable GPS ✅
            while (isActive) {
                try {
                    if (usingRoot) {
                        rootSpoofer.injectLocation(lat, lng)
                    } else {
                        normalSpoofer.pushLocation(LocationManager.GPS_PROVIDER,     lat, lng)
                        normalSpoofer.pushLocation(LocationManager.NETWORK_PROVIDER, lat, lng)
                        normalSpoofer.pushLocation("fused",                          lat, lng)
                    }
                    pushCount++

                    // Update display coordinates every 1 second (every 5 pushes)
                    // This is what UI and other apps SEE — natural 1 second update
                    if (pushCount % 5L == 0L) {
                        currentLat = lat
                        currentLng = lng
                    }

                } catch (e: Exception) {
                    Log.w("Service", "Push error: ${e.message}")
                }
                delay(PUSH_INTERVAL_MS)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning  = false
        isRootMode = false
        scope.cancel()
        // GPS was never disabled — nothing to restore ✅
        if (usingRoot) rootSpoofer.stopSpoofing()
        else           normalSpoofer.stopSpoofing()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Location Service",
            NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(lat: Double, lng: Double): Notification {
        val stopPI = PendingIntent.getService(this, 0,
            Intent(this, MockLocationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)
        val openPI = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val modeLabel = if (usingRoot) "ROOT MODE 🔓" else "NORMAL MODE"
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 Location Active — $modeLabel")
            .setContentText("Lat: %.5f   Lng: %.5f".format(lat, lng))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openPI)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_delete, "Stop", stopPI).build())
            .setOngoing(true).build()
    }
}

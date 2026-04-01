package com.mocklocation.stealth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed — checking if spoofing should restart")
            val prefs = context.getSharedPreferences("stealth_prefs", Context.MODE_PRIVATE)
            val wasRunning = prefs.getBoolean("was_running", false)
            val lat = prefs.getFloat("last_lat", 0f).toDouble()
            val lng = prefs.getFloat("last_lng", 0f).toDouble()

            if (wasRunning && lat != 0.0 && lng != 0.0) {
                Log.d("BootReceiver", "Restarting spoofing at $lat, $lng")
                Intent(context, MockLocationService::class.java).also {
                    it.putExtra(MockLocationService.EXTRA_LAT, lat)
                    it.putExtra(MockLocationService.EXTRA_LNG, lng)
                    context.startForegroundService(it)
                }
            }
        }
    }
}

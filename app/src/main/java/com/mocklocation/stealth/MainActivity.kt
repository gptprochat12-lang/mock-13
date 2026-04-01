package com.mocklocation.stealth

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mocklocation.stealth.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var normalSpoofer: NormalLocationSpoofer
    private var isRooted = false

    // ── Custom presets storage key ────────────────────────────────
    private val PREFS_NAME    = "stealth_prefs"
    private val KEY_PRESETS   = "custom_presets"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.all { it.value }) checkDeviceStatus()
        else toast("Location permission required!")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        normalSpoofer = NormalLocationSpoofer(this)
        binding.tvRootStatus.text = "🔄  Checking root status..."
        binding.tvRootHint.text   = "Please wait..."
        requestPermissionsIfNeeded()
        checkDeviceStatus()
        setupUI()
        startLiveAddressUpdater()
        loadCustomPresets()
    }

    override fun onResume() {
        super.onResume()
        checkDeviceStatus()
        updateStatusUI()
    }

    // ── Root detection ────────────────────────────────────────────
    private fun checkDeviceStatus() {
        lifecycleScope.launch {
            val magiskFiles    = withContext(Dispatchers.IO) { RootChecker.checkMagiskFiles() }
            val suWorks        = withContext(Dispatchers.IO) { RootChecker.isSuAvailable() }
            val magiskInstalled= withContext(Dispatchers.IO) { RootChecker.isMagiskInstalled(applicationContext) }
            isRooted = magiskFiles || suWorks || magiskInstalled
            updateRootBadge(magiskFiles, suWorks, magiskInstalled)
        }
    }

    private fun updateRootBadge(magiskFiles: Boolean, suWorks: Boolean, magiskInstalled: Boolean) {
        val reason = when {
            suWorks         -> "su command ✅"
            magiskFiles     -> "Magisk files ✅"
            magiskInstalled -> "Magisk app ✅"
            else            -> "not detected"
        }
        if (isRooted) {
            binding.tvRootStatus.text = "🔓  ROOT MODE — $reason"
            binding.tvRootStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            binding.tvRootHint.text = "✅ No Developer Mode needed"
            binding.btnGrantRoot.text = "🔄 Refresh Root Status"
        } else {
            binding.tvRootStatus.text = "🔒  Root not detected"
            binding.tvRootStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            binding.tvRootHint.text = "Tap Grant Root → then GRANT in Magisk popup"
            binding.btnGrantRoot.text = "🔑 GRANT ROOT PERMISSION"
        }
    }

    // ── Live address updater (every 200ms, offline geocoder) ─────
    private fun startLiveAddressUpdater() {
        lifecycleScope.launch {
            while (isActive) {
                if (MockLocationService.isRunning) {
                    val lat = MockLocationService.currentLat
                    val lng = MockLocationService.currentLng
                    // Update coords display
                    binding.tvCurrentCoords.text =
                        "%.6f, %.6f".format(lat, lng)
                    // Reverse geocode offline (Android Geocoder — no internet needed)
                    val address = withContext(Dispatchers.IO) {
                        getAddressFromCoords(lat, lng)
                    }
                    binding.tvLocationName.text = address
                } else {
                    binding.tvCurrentCoords.text = "—"
                    binding.tvLocationName.text  = "—"
                }
                delay(200L) // Update every 200ms matching push interval
            }
        }
    }

    // ── Offline reverse geocoding ─────────────────────────────────
    private fun getAddressFromCoords(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(applicationContext, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var result = "Locating..."
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    result = formatAddress(addresses.firstOrNull())
                }
                // Small wait for callback
                Thread.sleep(100)
                result
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                formatAddress(addresses?.firstOrNull())
            }
        } catch (e: Exception) {
            "%.5f, %.5f".format(lat, lng)
        }
    }

    private fun formatAddress(address: android.location.Address?): String {
        if (address == null) return "Unknown location"
        val parts = mutableListOf<String>()
        // Build address: Thoroughfare (street), SubLocality, Locality (city)
        address.thoroughfare?.let    { parts.add(it) }
        address.subLocality?.let     { if (it !in parts) parts.add(it) }
        address.locality?.let        { if (it !in parts) parts.add(it) }
        address.adminArea?.let       { if (parts.size < 2) parts.add(it) }
        return if (parts.isNotEmpty()) parts.take(2).joinToString(", ")
        else "%.5f, %.5f".format(address.latitude, address.longitude)
    }

    // ── UI setup ──────────────────────────────────────────────────
    private fun setupUI() {
        binding.btnGrantRoot.setOnClickListener { requestRootPermission() }

        binding.btnStart.setOnClickListener {
            val lat = binding.etLatitude.text.toString().trim().toDoubleOrNull()
            val lng = binding.etLongitude.text.toString().trim().toDoubleOrNull()
            if (lat == null || lng == null) { toast("Enter valid coordinates!"); return@setOnClickListener }
            if (lat < -90  || lat > 90)    { toast("Latitude: -90 to 90");      return@setOnClickListener }
            if (lng < -180 || lng > 180)   { toast("Longitude: -180 to 180");   return@setOnClickListener }
            if (!isRooted && !normalSpoofer.isMockPermissionGranted()) {
                startActivity(Intent(this, SetupGuideActivity::class.java))
                return@setOnClickListener
            }
            startSpoofing(lat, lng)
        }

        binding.btnStop.setOnClickListener { stopSpoofing() }

        // Save current coords as custom preset
        binding.btnSavePreset.setOnClickListener {
            val lat = binding.etLatitude.text.toString().trim().toDoubleOrNull()
            val lng = binding.etLongitude.text.toString().trim().toDoubleOrNull()
            if (lat == null || lng == null) { toast("Enter coordinates first!"); return@setOnClickListener }
            showSavePresetDialog(lat, lng)
        }
    }

    // ── Custom presets ────────────────────────────────────────────
    private fun loadCustomPresets() {
        val prefs    = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr  = prefs.getString(KEY_PRESETS, "[]") ?: "[]"
        val presets  = parsePresets(jsonStr)
        renderPresets(presets)
    }

    private fun renderPresets(presets: List<Triple<String, Double, Double>>) {
        val container = binding.presetContainer
        container.removeAllViews()

        if (presets.isEmpty()) {
            val tv = android.widget.TextView(this).apply {
                text = "No saved presets yet.\nEnter coordinates and tap SAVE PRESET +"
                setTextColor(getColor(android.R.color.darker_gray))
                textSize = 13f
                setPadding(8, 16, 8, 16)
            }
            container.addView(tv)
            return
        }

        presets.forEachIndexed { index, (name, lat, lng) ->
            // Row layout
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
            }

            // Preset button
            val btn = android.widget.Button(this).apply {
                text = "📍 $name"
                textSize = 12f
                setBackgroundColor(getColor(android.R.color.holo_blue_dark))
                setTextColor(getColor(android.R.color.white))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply { marginEnd = 8 }
                setOnClickListener {
                    binding.etLatitude.setText(lat.toString())
                    binding.etLongitude.setText(lng.toString())
                    binding.tvPresetLabel.text = "📍 $name"
                    toast("📍 $name selected")
                }
            }

            // Delete button
            val del = android.widget.Button(this).apply {
                text = "✕"
                textSize = 12f
                setBackgroundColor(getColor(android.R.color.holo_red_dark))
                setTextColor(getColor(android.R.color.white))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    deletePreset(index)
                }
            }

            row.addView(btn)
            row.addView(del)
            container.addView(row)
        }
    }

    private fun showSavePresetDialog(lat: Double, lng: Double) {
        val input = android.widget.EditText(this).apply {
            hint = "e.g. Dewoo Stand, Gujrat"
            setPadding(32, 24, 32, 24)
        }
        // Suggest address as default name
        lifecycleScope.launch {
            val address = withContext(Dispatchers.IO) { getAddressFromCoords(lat, lng) }
            withContext(Dispatchers.Main) { input.setText(address) }
        }

        AlertDialog.Builder(this)
            .setTitle("💾 Save Location Preset")
            .setMessage("Lat: $lat\nLng: $lng")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) { toast("Enter a name!"); return@setPositiveButton }
                savePreset(name, lat, lng)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePreset(name: String, lat: Double, lng: Double) {
        val prefs   = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PRESETS, "[]") ?: "[]"
        val list    = parsePresets(jsonStr).toMutableList()
        list.add(Triple(name, lat, lng))
        prefs.edit().putString(KEY_PRESETS, presetsToJson(list)).apply()
        renderPresets(list)
        toast("✅ Preset '$name' saved!")
    }

    private fun deletePreset(index: Int) {
        val prefs   = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PRESETS, "[]") ?: "[]"
        val list    = parsePresets(jsonStr).toMutableList()
        if (index < list.size) {
            val name = list[index].first
            list.removeAt(index)
            prefs.edit().putString(KEY_PRESETS, presetsToJson(list)).apply()
            renderPresets(list)
            toast("🗑️ '$name' deleted")
        }
    }

    private fun parsePresets(json: String): List<Triple<String, Double, Double>> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Triple(obj.getString("name"), obj.getDouble("lat"), obj.getDouble("lng"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun presetsToJson(presets: List<Triple<String, Double, Double>>): String {
        val arr = JSONArray()
        presets.forEach { (name, lat, lng) ->
            arr.put(JSONObject().apply {
                put("name", name); put("lat", lat); put("lng", lng)
            })
        }
        return arr.toString()
    }

    // ── Root permission request ───────────────────────────────────
    private fun requestRootPermission() {
        binding.tvRootStatus.text = "🔄  Requesting root..."
        lifecycleScope.launch {
            val granted     = withContext(Dispatchers.IO) { RootChecker.isSuAvailable() }
            val magiskFiles = withContext(Dispatchers.IO) { RootChecker.checkMagiskFiles() }
            val magiskInst  = withContext(Dispatchers.IO) { RootChecker.isMagiskInstalled(applicationContext) }
            isRooted = granted || magiskFiles || magiskInst
            if (isRooted) {
                updateRootBadge(magiskFiles, granted, magiskInst)
                toast("✅ Root detected!")
            } else {
                showRootHelp()
            }
        }
    }

    private fun showRootHelp() {
        AlertDialog.Builder(this)
            .setTitle("Grant Root Permission")
            .setMessage(
                "Open Magisk → Settings → Superuser\n" +
                "→ Auto Response → Prompt\n\n" +
                "Then tap Grant Root here again."
            )
            .setPositiveButton("Try Again") { _, _ -> requestRootPermission() }
            .setNegativeButton("Open Magisk") { _, _ -> openMagisk() }
            .setNeutralButton("Use Dev Mode") { _, _ ->
                startActivity(Intent(this, SetupGuideActivity::class.java))
            }
            .show()
        updateRootBadge(false, false, false)
    }

    private fun openMagisk() {
        val packages = listOf("com.topjohnwu.magisk","io.github.huskydg.magisk","io.github.vvb2060.magisk")
        for (pkg in packages) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) { startActivity(intent); return }
            } catch (_: Exception) {}
        }
        toast("Open Magisk manually from app drawer")
    }

    // ── Spoofing control ──────────────────────────────────────────
    private fun startSpoofing(lat: Double, lng: Double) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("last_lat", lat.toFloat())
            .putFloat("last_lng", lng.toFloat())
            .putBoolean("was_running", true).apply()
        Intent(this, MockLocationService::class.java).also {
            it.putExtra(MockLocationService.EXTRA_LAT, lat)
            it.putExtra(MockLocationService.EXTRA_LNG, lng)
            startForegroundService(it)
        }
        updateStatusUI()
        toast("✅ Location spoofing started!")
    }

    private fun stopSpoofing() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("was_running", false).apply()
        stopService(Intent(this, MockLocationService::class.java))
        updateStatusUI()
        toast("⛔ Stopped")
    }

    private fun updateStatusUI() {
        val running = MockLocationService.isRunning
        binding.tvStatus.text = if (running) "🟢  ACTIVE" else "🔴  INACTIVE"
        binding.tvStatus.setTextColor(
            getColor(if (running) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        binding.btnStart.isEnabled = !running
        binding.btnStop.isEnabled  =  running
        binding.tvModeLabel.text = when {
            !running -> "Mode: Idle"
            MockLocationService.isRootMode -> "Mode: 🔓 Root (Stealth)"
            else -> "Mode: 🔒 Normal"
        }
    }

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val denied = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isNotEmpty()) permissionLauncher.launch(denied.toTypedArray())
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

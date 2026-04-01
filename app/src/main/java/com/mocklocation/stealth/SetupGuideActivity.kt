package com.mocklocation.stealth

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SetupGuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AlertDialog.Builder(this)
            .setTitle("⚙️ One-Time Setup Required")
            .setMessage(
                "Developer Mode is needed for normal mode.\n\n" +
                "1️⃣  Settings → About Phone\n" +
                "2️⃣  Tap Build Number 7 times\n" +
                "3️⃣  Developer Options → Mock Location App\n" +
                "4️⃣  Select 'StealthLocation'\n\n" +
                "OR root your device for automatic setup with no conflicts."
            )
            .setPositiveButton("Open Developer Options") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}

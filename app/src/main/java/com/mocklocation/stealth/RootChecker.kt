package com.mocklocation.stealth

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.DataOutputStream
import java.io.File

object RootChecker {

    private const val TAG = "RootChecker"

    // ── Main root check — tries multiple methods ──────────────────
    fun isRooted(): Boolean {
        return checkMagiskFiles()
            || checkSuBinary()
            || checkSuperuserApp()
    }

    // ── Try to execute su and get response ────────────────────────
    fun isSuAvailable(): Boolean {
        // Try method 1: DataOutputStream
        if (trySuWithDataOutputStream()) return true
        // Try method 2: Direct exec
        if (trySuDirectExec()) return true
        // Try method 3: Check Magisk files as fallback
        if (checkMagiskFiles()) return true
        return false
    }

    private fun trySuWithDataOutputStream(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitVal = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            os.close()
            val result = output.contains("uid=0") || output.contains("root")
            Log.d(TAG, "Method1: exit=$exitVal output=$output result=$result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Method1 failed: ${e.message}")
            false
        }
    }

    private fun trySuDirectExec(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            val result = output.contains("uid=0") || output.contains("root")
            Log.d(TAG, "Method2: output=$output result=$result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Method2 failed: ${e.message}")
            false
        }
    }

    // ── Check Magisk specific paths ───────────────────────────────
    fun checkMagiskFiles(): Boolean {
        val paths = arrayOf(
            "/data/adb/magisk",
            "/data/adb/magisk.db",
            "/data/adb/modules",
            "/sbin/.magisk",
            "/dev/.magisk",
            "/data/adb/magisk.img"
        )
        for (path in paths) {
            try {
                if (File(path).exists()) {
                    Log.d(TAG, "Magisk found at: $path")
                    return true
                }
            } catch (e: Exception) { }
        }
        return false
    }

    private fun checkSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )
        return paths.any {
            try { File(it).exists() } catch (e: Exception) { false }
        }
    }

    private fun checkSuperuserApp(): Boolean {
        val apps = arrayOf(
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk"
        )
        return apps.any {
            try { File(it).exists() } catch (e: Exception) { false }
        }
    }

    // ── Check if Magisk app is installed ──────────────────────────
    fun isMagiskInstalled(context: Context): Boolean {
        val packages = listOf(
            "com.topjohnwu.magisk",
            "io.github.huskydg.magisk",
            "io.github.vvb2060.magisk"
        )
        return packages.any { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}

package com.example.wake

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OvernightJournal {

    private const val FILE_NAME = "overnight_journal.txt"
    private const val MAX_ENTRIES = 50
    private val memoryLog = mutableListOf<String>()
    private val lock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(context: Context?, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedLine = "$timestamp [$tag] $message"

        Log.i(tag, message)
        WakePrefsManager.logWakeEvent("[$tag] $message")

        synchronized(lock) {
            memoryLog.add(formattedLine)
            while (memoryLog.size > MAX_ENTRIES) {
                memoryLog.removeAt(0)
            }

            if (context != null) {
                try {
                    val file = File(context.filesDir, FILE_NAME)
                    file.writeText(memoryLog.joinToString("\n"))
                } catch (e: Exception) {
                    Log.w("OvernightJournal", "Failed writing to journal file: ${e.message}")
                }
            }
        }
    }

    fun logPermissionsAudit(context: Context, source: String) {
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val overlayGranted = Settings.canDrawOverlays(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        val pipGranted = PermissionHelper.hasPipPermission(context)

        val auditMessage = "[$source] RECORD_AUDIO=$audioGranted, POST_NOTIFICATIONS=$notifGranted, " +
                "SYSTEM_ALERT_WINDOW=$overlayGranted, canScheduleExactAlarms=$canScheduleExact, " +
                "isIgnoringBatteryOptimizations=$isIgnoringBattery, PICTURE_IN_PICTURE=$pipGranted"

        log(context, "PERMISSION", auditMessage)
    }

    fun getJournal(context: Context): String {
        return synchronized(lock) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (file.exists()) {
                    file.readText()
                } else {
                    memoryLog.joinToString("\n")
                }
            } catch (e: Exception) {
                memoryLog.joinToString("\n")
            }
        }
    }

    fun getJournalLines(context: Context): List<String> {
        return synchronized(lock) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (file.exists()) {
                    file.readLines().takeLast(MAX_ENTRIES)
                } else {
                    memoryLog.toList()
                }
            } catch (e: Exception) {
                memoryLog.toList()
            }
        }
    }
}

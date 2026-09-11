package com.example.wake

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

object PermissionHelper {

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReadPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.areNotificationsEnabled() ?: true
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun hasPipPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    android.os.Process.myUid(),
                    context.packageName
                ) == android.app.AppOpsManager.MODE_ALLOWED
            } else {
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    android.os.Process.myUid(),
                    context.packageName
                ) == android.app.AppOpsManager.MODE_ALLOWED
            }
        } catch (e: Exception) {
            true
        }
    }

    fun openPipSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", Uri.parse("package:${context.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                OvernightJournal.log(context, "PERMS", "Opened PiP settings")
                return
            }
        } catch (e: Exception) {
            Log.w("PermissionHelper", "Failed opening PiP direct settings: ${e.message}")
        }
        try {
            context.startActivity(getAppSettingsIntent(context, newTask = true))
        } catch (_: Exception) {}
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                OvernightJournal.log(context, "PERMS", "Requested ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
            } catch (e: Exception) {
                Log.w("PermissionHelper", "Failed requesting ignore battery optimizations: ${e.message}")
            }
        }
    }

    fun areAllPermissionsGranted(context: Context): Boolean {
        return hasRecordAudioPermission(context) &&
                hasReadPhoneStatePermission(context) &&
                hasOverlayPermission(context) &&
                hasNotificationPermission(context) &&
                hasExactAlarmPermission(context)
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = getOverlaySettingsIntent(context, newTask = true)
            context.startActivity(intent)
            OvernightJournal.log(context, "PERMS", "Opened overlay settings")
        } catch (e: Exception) {
            Log.w("PermissionHelper", "Failed opening overlay settings: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val genericIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(genericIntent)
                } else {
                    context.startActivity(getAppSettingsIntent(context, newTask = true))
                }
            } catch (_: Exception) {}
        }
    }

    fun openExactAlarmSettings(context: Context) {
        try {
            val intent = getExactAlarmSettingsIntent(context, newTask = true)
            context.startActivity(intent)
            OvernightJournal.log(context, "PERMS", "Opened exact alarm settings")
        } catch (e: Exception) {
            Log.w("PermissionHelper", "Failed opening exact alarm settings: ${e.message}")
            try {
                context.startActivity(getAppSettingsIntent(context, newTask = true))
            } catch (_: Exception) {}
        }
    }

    fun openNotificationSettings(context: Context) {
        try {
            val intent = getNotificationSettingsIntent(context, newTask = true)
            context.startActivity(intent)
            OvernightJournal.log(context, "PERMS", "Opened notification settings")
        } catch (e: Exception) {
            Log.w("PermissionHelper", "Failed opening notification settings: ${e.message}")
            try {
                context.startActivity(getAppSettingsIntent(context, newTask = true))
            } catch (_: Exception) {}
        }
    }

    fun getOverlaySettingsIntent(context: Context, newTask: Boolean = false): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            getAppSettingsIntent(context, newTask = false)
        }
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    fun getNotificationSettingsIntent(context: Context, newTask: Boolean = false): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            getAppSettingsIntent(context, newTask = false)
        }
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    fun getExactAlarmSettingsIntent(context: Context, newTask: Boolean = false): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            getAppSettingsIntent(context, newTask = false)
        }
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    fun getAppSettingsIntent(context: Context, newTask: Boolean = false): Intent {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }
}

package com.example.wake

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrayerActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SNOOZE = "com.example.wake.ACTION_SNOOZE"
        const val ACTION_SKIP = "com.example.wake.ACTION_SKIP"

        // Native notification flow actions
        const val ACTION_NOTIF_PROCEED = "com.example.wake.ACTION_NOTIF_PROCEED"
        const val ACTION_NOTIF_SNOOZE_DEFAULT = "com.example.wake.ACTION_NOTIF_SNOOZE_DEFAULT"
        const val ACTION_NOTIF_SNOOZE_MENU = "com.example.wake.ACTION_NOTIF_SNOOZE_MENU"
        const val ACTION_NOTIF_SNOOZE_SELECT = "com.example.wake.ACTION_NOTIF_SNOOZE_SELECT"
        const val ACTION_NOTIF_DISMISS = "com.example.wake.ACTION_NOTIF_DISMISS"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"

        // Exact Alarm actions
        const val ACTION_ALARM_REMINDER = "com.example.wake.ACTION_ALARM_REMINDER"
        const val ACTION_ALARM_DOOR = "com.example.wake.ACTION_ALARM_DOOR"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = powerManager?.isInteractive ?: false
        val isDeviceIdleMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isDeviceIdleMode ?: false
        } else {
            false
        }
        val screenState = if (isInteractive) "ON" else "OFF"
        val ritualBefore = WakePrefsManager.isRitualPending(context)

        OvernightJournal.log(
            context,
            "SCHEDULER",
            "Alarm fired (action=${intent.action}) | isDeviceIdleMode=$isDeviceIdleMode, isInteractive=$isInteractive, screenState=$screenState, ritualPending=$ritualBefore"
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        when (intent.action) {
            // AlarmManager triggered 30-second reminder (T - 30s)
            ACTION_ALARM_REMINDER -> {
                Log.i("WakeDetector", "[ALARM] Received exact 30-second reminder trigger")
                if (WakePrefsManager.isReminderEnabled(context)) {
                    FirstLightNotificationHelper.postState1Reminder(context)
                    OvernightJournal.log(context, "SCHEDULER", "30s reminder notification posted (reminderEnabled=true)")
                } else {
                    OvernightJournal.log(context, "SCHEDULER", "30s reminder skipped (reminderEnabled=false)")
                }
            }

            // AlarmManager triggered main prayer door (Exact Time, T = 0)
            // If the user didn't choose snooze during the 30s window, the main overlay takes over immediately.
            ACTION_ALARM_DOOR -> {
                Log.i("WakeDetector", "[ALARM] Received exact prayer door trigger (30s window elapsed)")
                acquireWakeLock(context)
                FirstLightNotificationHelper.cancelNotification(context)
                
                WakePrefsManager.setRitualPending(context, true, reason = "alarm door")
                
                WakePrefsManager.setSnoozeUntil(context, 0L)
                WakePrefsManager.logWakeEvent("[ALARM] Prayer time reached — displaying full screen door")

                // Schedule next prayer for tomorrow
                PrayerAlarmScheduler.scheduleNextPrayer(context)

                // Trigger prayer door full-screen overlay (WindowManager TYPE_APPLICATION_OVERLAY)
                WakeOverlayManager.triggerPrayerDoor(context)
            }

            // User clicked "✓ Proceed" from notification -> Immediately trigger the prayer door without waiting
            ACTION_NOTIF_PROCEED -> {
                Log.i("WakeDetector", "[NOTIF] User clicked Proceed — launching prayer door immediately")
                acquireWakeLock(context)
                FirstLightNotificationHelper.cancelNotification(context)
                
                WakePrefsManager.setRitualPending(context, true, reason = "notification proceed")
                
                WakePrefsManager.setSnoozeUntil(context, 0L)
                WakePrefsManager.logWakeEvent("[NOTIF] Proceed clicked — prayer door launched")

                // Schedule next prayer for tomorrow
                PrayerAlarmScheduler.scheduleNextPrayer(context)

                // Launch prayer door right away
                WakeOverlayManager.triggerPrayerDoor(context)
            }

            // User clicked 1-click snooze (default duration, e.g. 15 min)
            ACTION_NOTIF_SNOOZE_DEFAULT -> {
                val defaultMin = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, WakePrefsManager.getDefaultSnoozeMinutes(context))
                val now = System.currentTimeMillis()
                val snoozeTime = now + (defaultMin * 60 * 1000L)
                WakePrefsManager.setSnoozeUntil(context, snoozeTime)
                WakePrefsManager.setRitualPending(context, false, reason = "Snooze")

                // Schedule exact alarm for the snoozed time
                PrayerAlarmScheduler.scheduleSnooze(context, snoozeTime)

                val timeFormatted = SimpleDateFormat("h:mm a", Locale.US).format(Date(snoozeTime))
                val logMsg = "[NOTIF] 1-Click Snoozed $defaultMin min until $timeFormatted"
                Log.i("WakeDetector", logMsg)
                WakePrefsManager.logWakeEvent(logMsg)
                OvernightJournal.log(context, "SCHEDULER", logMsg)

                // Show confirmation notification
                FirstLightNotificationHelper.postState3Confirmation(context, snoozeTime)
            }

            // User clicked "⏱ More" from notification -> update notification with choices in-place
            ACTION_NOTIF_SNOOZE_MENU -> {
                Log.i("WakeDetector", "[NOTIF] User tapped More, updating notification with choices")
                FirstLightNotificationHelper.postState2SnoozeOptions(context)
                WakePrefsManager.logWakeEvent("[NOTIF] Snooze menu opened")
            }

            // User selected specific duration (e.g. 15 min, 30 min, 1 hour)
            ACTION_NOTIF_SNOOZE_SELECT -> {
                val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 15)
                val now = System.currentTimeMillis()
                val snoozeTime = now + (snoozeMinutes * 60 * 1000L)
                WakePrefsManager.setSnoozeUntil(context, snoozeTime)
                WakePrefsManager.setRitualPending(context, false, reason = "Snooze")

                // Schedule exact alarm for the snoozed time
                PrayerAlarmScheduler.scheduleSnooze(context, snoozeTime)

                val timeFormatted = SimpleDateFormat("h:mm a", Locale.US).format(Date(snoozeTime))
                val logMsg = "[NOTIF] Snoozed $snoozeMinutes min until $timeFormatted"
                Log.i("WakeDetector", logMsg)
                WakePrefsManager.logWakeEvent(logMsg)
                OvernightJournal.log(context, "SCHEDULER", logMsg)

                // Show confirmation notification
                FirstLightNotificationHelper.postState3Confirmation(context, snoozeTime)
            }

            ACTION_NOTIF_DISMISS -> {
                Log.i("WakeDetector", "[NOTIF] Dismissed snooze options")
                FirstLightNotificationHelper.cancelNotification(context)
            }

            ACTION_SNOOZE -> {
                val defaultMin = WakePrefsManager.getDefaultSnoozeMinutes(context)
                val snoozeTime = System.currentTimeMillis() + (defaultMin * 60 * 1000L)
                WakePrefsManager.setSnoozeUntil(context, snoozeTime)
                WakePrefsManager.setRitualPending(context, false, reason = "Snooze")
                PrayerAlarmScheduler.scheduleSnooze(context, snoozeTime)
                val timeFormatted = SimpleDateFormat("h:mm a", Locale.US).format(Date(snoozeTime))
                val msg = "[SCHEDULER] snoozed until $timeFormatted"
                Log.i("WakeDetector", msg)
                WakePrefsManager.logWakeEvent(msg)
                OvernightJournal.log(context, "SCHEDULER", msg)
                FirstLightNotificationHelper.cancelNotification(context)
            }

            ACTION_SKIP -> {
                WakePrefsManager.setRitualPending(context, false, reason = "Skip today")
                val msg = "[WAKE] Skipped today"
                Log.i("WakeDetector", msg)
                WakePrefsManager.logWakeEvent(msg)
                FirstLightNotificationHelper.cancelNotification(context)
                PrayerAlarmScheduler.scheduleNextPrayer(context)
            }
        }
    }

    private fun acquireWakeLock(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "FirstLight:MorningWakeLock"
            )
            wakeLock?.acquire(3 * 60 * 1000L) // 3 minutes
            OvernightJournal.log(context, "WAKE", "Acquired SCREEN_BRIGHT_WAKE_LOCK with ACQUIRE_CAUSES_WAKEUP (3 min)")
        } catch (e: Exception) {
            OvernightJournal.log(context, "WAKE", "Failed acquiring wakeLock: ${e.message}")
        }
    }
}

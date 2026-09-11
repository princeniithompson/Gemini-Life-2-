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
        const val ACTION_ALARM_RESUME = "com.example.wake.ACTION_ALARM_RESUME"
        const val ACTION_ALARM_CUTOFF = "com.example.wake.ACTION_ALARM_CUTOFF"
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
                val cycleId = intent.getLongExtra("trigger_time", 0L).let {
                    if (it > 0L) it else PrayerAlarmScheduler.currentScheduledCycleId
                }
                Log.i("WakeDetector", "[ALARM] Received exact 30-second reminder trigger (cycle=$cycleId)")

                // Run connectivity check first: OFFLINE -> silence, post nothing
                val isOnline = ConnectivityChecker.hasValidatedInternet(context)
                if (!isOnline) {
                    val reason = "offline (suppressing reminder, silence)"
                    Log.i("WakeDetector", "[ALARM] trigger for cycle $cycleId gate decision: OFFLINE ($reason)")
                    OvernightJournal.log(context, "SCHEDULER", "trigger for cycle $cycleId gate decision: OFFLINE ($reason)")
                    return
                }
                Log.i("WakeDetector", "[ALARM] trigger for cycle $cycleId gate decision: ONLINE")
                OvernightJournal.log(context, "SCHEDULER", "trigger for cycle $cycleId gate decision: ONLINE")

                if (WakeOverlayManager.isSessionActive()) {
                    val reason = "session currently active"
                    Log.i("WakeDetector", "[ALARM] trigger for cycle $cycleId rejected: $reason")
                    OvernightJournal.log(context, "SCHEDULER", "trigger for cycle $cycleId rejected: $reason")
                    return
                }
                if (cycleId > 0L && WakeOverlayManager.getServedCycleId() == cycleId) {
                    val reason = "cycle already served"
                    Log.i("WakeDetector", "[ALARM] trigger for cycle $cycleId rejected: $reason")
                    OvernightJournal.log(context, "SCHEDULER", "trigger for cycle $cycleId rejected: $reason")
                    return
                }

                val reason = "30s reminder"
                Log.i("WakeDetector", "[ALARM] trigger for cycle $cycleId accepted: $reason")
                OvernightJournal.log(context, "SCHEDULER", "trigger for cycle $cycleId accepted: $reason")

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
                val cycleId = intent.getLongExtra("trigger_time", 0L).let {
                    if (it > 0L) it else PrayerAlarmScheduler.currentScheduledCycleId
                }
                Log.i("WakeDetector", "[ALARM] Received exact prayer door trigger (cycle=$cycleId)")

                // Schedule next prayer for tomorrow (always books - chain intact)
                PrayerAlarmScheduler.scheduleNextPrayer(context)

                // Run connectivity check first
                val isOnline = ConnectivityChecker.hasValidatedInternet(context)
                if (!isOnline) {
                    WakePrefsManager.setPrayerDeferredOffline(context, true)
                    WakePrefsManager.setDeferredDay(context, WakePrefsManager.getTodayString())
                    PrayerAlarmScheduler.scheduleMidnightCutoff(context)
                    DeferredResumeWorker.enqueue(context)

                    val notifiedCycleId = WakePrefsManager.getOfflineNotifiedCycleId(context)
                    if (notifiedCycleId != cycleId) {
                        FirstLightNotificationHelper.postOfflineNotification(context)
                        WakePrefsManager.setOfflineNotifiedCycleId(context, cycleId)
                        Log.i("WakeDetector", "[ALARM] offline notification posted for cycle $cycleId")
                        OvernightJournal.log(context, "SCHEDULER", "offline notification posted for cycle $cycleId")
                    } else {
                        Log.i("WakeDetector", "[ALARM] suppressed: cycle $cycleId already notified")
                        OvernightJournal.log(context, "SCHEDULER", "suppressed: cycle $cycleId already notified")
                    }
                    return
                }

                // ONLINE -> show the door exactly as today
                Log.i("WakeDetector", "[ALARM] trigger for cycle $cycleId gate decision: ONLINE -> proceeding to show door")
                OvernightJournal.log(context, "SCHEDULER", "trigger for cycle $cycleId gate decision: ONLINE -> proceeding to show door")

                WakePrefsManager.clearDeferredPrayer(context)
                DeferredResumeWorker.cancel(context)
                PrayerAlarmScheduler.cancelMidnightCutoff(context)
                PrayerAlarmScheduler.cancelResumeAlarm(context)

                // Trigger prayer door full-screen overlay (WindowManager TYPE_APPLICATION_OVERLAY)
                val success = WakeOverlayManager.triggerPrayerDoor(context, cycleId)
                if (!success) {
                    return
                }

                WakeOverlayManager.acquireWakeLock(context)
                FirstLightNotificationHelper.cancelNotification(context)
                
                WakePrefsManager.setRitualPending(context, true, reason = "alarm door")
                WakePrefsManager.setSnoozeUntil(context, 0L)
                WakePrefsManager.logWakeEvent("[ALARM] Prayer time reached — displaying full screen door")
            }

            // User clicked "✓ Proceed" from notification -> Immediately trigger the prayer door without waiting
            ACTION_NOTIF_PROCEED -> {
                val cycleId = intent.getLongExtra("trigger_time", 0L).let {
                    if (it > 0L) it else PrayerAlarmScheduler.currentScheduledCycleId
                }
                Log.i("WakeDetector", "[NOTIF] User clicked Proceed (cycle=$cycleId)")

                // Cancel pending alarms for today's cycle immediately (Proceed / Pray Now path)
                PrayerAlarmScheduler.cancelPendingPrayerAlarms(context)

                // Schedule next prayer for tomorrow (always books)
                PrayerAlarmScheduler.scheduleNextPrayer(context)

                WakePrefsManager.clearDeferredPrayer(context)
                DeferredResumeWorker.cancel(context)
                PrayerAlarmScheduler.cancelMidnightCutoff(context)
                PrayerAlarmScheduler.cancelResumeAlarm(context)

                // Launch prayer door right away
                val success = WakeOverlayManager.triggerPrayerDoor(context, cycleId)
                if (!success) {
                    return
                }

                WakeOverlayManager.acquireWakeLock(context)
                FirstLightNotificationHelper.cancelNotification(context)
                
                WakePrefsManager.setRitualPending(context, true, reason = "notification proceed")
                WakePrefsManager.setSnoozeUntil(context, 0L)
                WakePrefsManager.logWakeEvent("[NOTIF] Proceed clicked — prayer door launched")
            }

            // User clicked 1-click snooze (default duration, e.g. 15 min)
            ACTION_NOTIF_SNOOZE_DEFAULT -> {
                WakeOverlayManager.resetSessionLock(context)
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
                WakeOverlayManager.resetSessionLock(context)
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
                WakeOverlayManager.resetSessionLock(context)
                FirstLightNotificationHelper.cancelNotification(context)
            }

            ACTION_SNOOZE -> {
                WakeOverlayManager.resetSessionLock(context)
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
                WakeOverlayManager.resetSessionLock(context)
                WakePrefsManager.setRitualPending(context, false, reason = "Skip today")
                val msg = "[WAKE] Skipped today"
                Log.i("WakeDetector", msg)
                WakePrefsManager.logWakeEvent(msg)
                FirstLightNotificationHelper.cancelNotification(context)
                WakePrefsManager.clearDeferredPrayer(context)
                DeferredResumeWorker.cancel(context)
                PrayerAlarmScheduler.cancelMidnightCutoff(context)
                PrayerAlarmScheduler.cancelResumeAlarm(context)
                PrayerAlarmScheduler.scheduleNextPrayer(context)
            }

            ACTION_ALARM_RESUME -> {
                Log.i("WakeDetector", "[RESUME] Alarm fired for resume cooldown")
                if (!WakePrefsManager.isPrayerDeferredOffline(context)) {
                    Log.i("WakeDetector", "[RESUME] Prayer is not deferred, ignoring")
                    return
                }
                val deferredDay = WakePrefsManager.getDeferredDay(context)
                val today = WakePrefsManager.getTodayString()
                if (deferredDay.isNotBlank() && deferredDay != today) {
                    Log.i("WakeDetector", "[RESUME] deferredDay ($deferredDay) != today ($today), cleaning up")
                    OvernightJournal.log(context, "SCHEDULER", "deferredDay ($deferredDay) != today ($today), cleaning up")
                    WakePrefsManager.clearDeferredPrayer(context)
                    FirstLightNotificationHelper.cancelNotification(context)
                    DeferredResumeWorker.cancel(context)
                    return
                }

                val isOnline = ConnectivityChecker.hasValidatedInternet(context)
                if (isOnline) {
                    Log.i("WakeDetector", "[RESUME] Online validated -> resuming prayer with 30s reminder")
                    OvernightJournal.log(context, "SCHEDULER", "[RESUME] Online validated -> resuming prayer with 30s reminder")

                    // 1. Cancel the deferred notification
                    FirstLightNotificationHelper.cancelNotification(context)

                    // 2. Post the reminder "Prayer starts in 30 seconds" (existing builder, Snooze + Proceed unchanged)
                    FirstLightNotificationHelper.postState1Reminder(context)

                    // 3. Schedule the door alarm at +30s using an AD-HOC cycle id (like Pray Now) so the guard accepts it
                    val adHocCycleId = System.currentTimeMillis() + 30_000L
                    PrayerAlarmScheduler.scheduleAdHocDoor(context, adHocCycleId, delayMillis = 30_000L)
                } else {
                    Log.i("WakeDetector", "[RESUME] Offline again -> silent re-defer (keeping notification visible)")
                    OvernightJournal.log(context, "SCHEDULER", "[RESUME] Offline again -> silent re-defer")
                    DeferredResumeWorker.enqueue(context)
                }
            }

            ACTION_ALARM_CUTOFF -> {
                Log.i("WakeDetector", "[CUTOFF] defer window closed at midnight")
                OvernightJournal.log(context, "SCHEDULER", "defer window closed at midnight")
                WakePrefsManager.clearDeferredPrayer(context)
                FirstLightNotificationHelper.cancelNotification(context)
                DeferredResumeWorker.cancel(context)
                PrayerAlarmScheduler.cancelResumeAlarm(context)
            }

            Intent.ACTION_USER_PRESENT -> {
                if (WakePrefsManager.isPrayerDeferredOffline(context)) {
                    val deferredDay = WakePrefsManager.getDeferredDay(context)
                    val today = WakePrefsManager.getTodayString()
                    if (deferredDay == today) {
                        val isOnline = ConnectivityChecker.hasValidatedInternet(context)
                        if (isOnline) {
                            Log.i("WakeDetector", "[ACCELERATOR] USER_PRESENT + deferred + validated internet -> scheduleResumeCooldown()")
                            OvernightJournal.log(context, "SCHEDULER", "[ACCELERATOR] USER_PRESENT + deferred + validated internet -> scheduleResumeCooldown()")
                            PrayerAlarmScheduler.scheduleResumeCooldown(context)
                        }
                    } else if (deferredDay.isNotBlank()) {
                        WakePrefsManager.clearDeferredPrayer(context)
                        FirstLightNotificationHelper.cancelNotification(context)
                        DeferredResumeWorker.cancel(context)
                    }
                }
            }
        }
    }
}

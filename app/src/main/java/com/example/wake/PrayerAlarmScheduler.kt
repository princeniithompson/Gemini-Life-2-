package com.example.wake

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PrayerAlarmScheduler {

    const val REQUEST_CODE_REMINDER = 501
    const val REQUEST_CODE_DOOR = 502
    const val REQUEST_CODE_RESUME = 503
    const val REQUEST_CODE_CUTOFF = 504

    @Volatile
    var currentScheduledCycleId: Long = 0L

    fun cancelPendingPrayerAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        try {
            val reminderIntent = Intent(context, PrayerActionReceiver::class.java).apply {
                action = PrayerActionReceiver.ACTION_ALARM_REMINDER
            }
            val reminderPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_REMINDER,
                reminderIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(reminderPendingIntent)
            reminderPendingIntent.cancel()
            Log.i("WakeDetector", "[SCHEDULER] Cancelled pending 30s reminder alarm")
            OvernightJournal.log(context, "SCHEDULER", "Cancelled pending 30s reminder alarm")
        } catch (e: Exception) {
            Log.w("WakeDetector", "[SCHEDULER] Error cancelling reminder alarm: ${e.message}")
        }

        try {
            val doorIntent = Intent(context, PrayerActionReceiver::class.java).apply {
                action = PrayerActionReceiver.ACTION_ALARM_DOOR
            }
            val doorPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_DOOR,
                doorIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(doorPendingIntent)
            doorPendingIntent.cancel()
            Log.i("WakeDetector", "[SCHEDULER] Cancelled pending prayer door alarm")
            OvernightJournal.log(context, "SCHEDULER", "Cancelled pending prayer door alarm")
        } catch (e: Exception) {
            Log.w("WakeDetector", "[SCHEDULER] Error cancelling door alarm: ${e.message}")
        }

        cancelResumeAlarm(context)
        cancelMidnightCutoff(context)
    }

    fun cancelResumeAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            val resumeIntent = Intent(context, PrayerActionReceiver::class.java).apply {
                action = PrayerActionReceiver.ACTION_ALARM_RESUME
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_RESUME,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i("WakeDetector", "[SCHEDULER] Cancelled pending resume cooldown alarm")
            OvernightJournal.log(context, "SCHEDULER", "Cancelled pending resume cooldown alarm")
        } catch (e: Exception) {
            Log.w("WakeDetector", "[SCHEDULER] Error cancelling resume alarm: ${e.message}")
        }
    }

    fun cancelMidnightCutoff(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            val cutoffIntent = Intent(context, PrayerActionReceiver::class.java).apply {
                action = PrayerActionReceiver.ACTION_ALARM_CUTOFF
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_CUTOFF,
                cutoffIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i("WakeDetector", "[SCHEDULER] Cancelled pending midnight cutoff alarm")
            OvernightJournal.log(context, "SCHEDULER", "Cancelled pending midnight cutoff alarm")
        } catch (e: Exception) {
            Log.w("WakeDetector", "[SCHEDULER] Error cancelling cutoff alarm: ${e.message}")
        }
    }

    fun scheduleResumeCooldown(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // Cancel any pending resume alarm first (idempotent)
        cancelResumeAlarm(context)

        val now = System.currentTimeMillis()
        val resumeTime = now + 60_000L
        val resumeIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_ALARM_RESUME
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_RESUME,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExactAlarm(context, alarmManager, resumeTime, pendingIntent, "resume-cooldown")
        val formatted = SimpleDateFormat("yyyy-MM-dd h:mm:ss a", Locale.US).format(Date(resumeTime))
        Log.i("WakeDetector", "[SCHEDULER] Scheduled resume cooldown alarm for $formatted (now + 60s)")
        OvernightJournal.log(context, "SCHEDULER", "Scheduled resume cooldown alarm for $formatted (now + 60s)")
    }

    fun scheduleMidnightCutoff(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancelMidnightCutoff(context)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val cutoffTime = cal.timeInMillis
        val cutoffIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_ALARM_CUTOFF
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CUTOFF,
            cutoffIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExactAlarm(context, alarmManager, cutoffTime, pendingIntent, "midnight-cutoff")
        val formatted = SimpleDateFormat("yyyy-MM-dd h:mm:ss a", Locale.US).format(Date(cutoffTime))
        Log.i("WakeDetector", "[SCHEDULER] Scheduled midnight cutoff alarm for $formatted")
        OvernightJournal.log(context, "SCHEDULER", "Scheduled midnight cutoff alarm for $formatted")
    }

    fun scheduleAdHocDoor(context: Context, cycleId: Long, delayMillis: Long = 30_000L) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTime = System.currentTimeMillis() + delayMillis
        currentScheduledCycleId = cycleId
        val doorIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_ALARM_DOOR
            putExtra("trigger_time", cycleId)
        }
        val doorPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DOOR,
            doorIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarmClockAlarm(context, alarmManager, triggerTime, doorPendingIntent, "ad-hoc-resume-door")
        val formatted = SimpleDateFormat("yyyy-MM-dd h:mm:ss a", Locale.US).format(Date(triggerTime))
        Log.i("WakeDetector", "[SCHEDULER] Scheduled ad-hoc resume door alarm for $formatted (cycleId=$cycleId)")
        OvernightJournal.log(context, "SCHEDULER", "Scheduled ad-hoc resume door alarm for $formatted (cycleId=$cycleId)")
    }

    fun scheduleNextPrayer(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val now = System.currentTimeMillis()
        val snoozeUntil = WakePrefsManager.getSnoozeUntil(context)
        val isSnoozed = snoozeUntil > now

        val triggerTime: Long = if (isSnoozed) {
            snoozeUntil
        } else {
            val (hour, minute) = WakePrefsManager.getPrayerHourMinute(context)
            val scheduledDays = WakePrefsManager.getPrayerScheduledDays(context)
            
            // Find the earliest matching scheduled day at the configured hour:minute
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            // Check up to 8 consecutive days to find the next active scheduled prayer time
            var foundTime: Long = 0L
            for (i in 0..7) {
                val candidateTime = cal.timeInMillis
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val isToday = (i == 0)
                
                // If checking today, it is only valid if prayer time is in the future (or within 30s)
                val isUpcoming = if (isToday) candidateTime > now - 30_000L else true
                
                if (isUpcoming && scheduledDays.contains(dayOfWeek)) {
                    foundTime = candidateTime
                    break
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            // Fallback: if no day matched or all days unselected, default to tomorrow at the set time
            if (foundTime == 0L) {
                val fallbackCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (now > timeInMillis - 30_000L) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                foundTime = fallbackCal.timeInMillis
            }
            foundTime
        }

        currentScheduledCycleId = triggerTime

        val isReminderEnabled = WakePrefsManager.isReminderEnabled(context)
        // 30 seconds before exact prayer time (ONLY for original scheduled prayer, NEVER for snoozes)
        val reminderTime = triggerTime - 30_000L

        // 1. Schedule 30-second reminder alarm ONLY if not in a snoozed state
        if (!isSnoozed && isReminderEnabled && reminderTime > now) {
            val reminderIntent = Intent(context, PrayerActionReceiver::class.java).apply {
                action = PrayerActionReceiver.ACTION_ALARM_REMINDER
                putExtra("trigger_time", triggerTime)
            }
            val reminderPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_REMINDER,
                reminderIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val remFormatted = SimpleDateFormat("yyyy-MM-dd h:mm:ss a", Locale.US).format(Date(reminderTime))
            setExactAlarm(context, alarmManager, reminderTime, reminderPendingIntent, "30s-reminder")
            OvernightJournal.log(context, "SCHEDULER", "Scheduled 30s reminder alarm for $remFormatted")
        }

        // 2. Schedule Main Prayer Door Trigger Alarm (Exact time)
        if (triggerTime > now) {
            val doorIntent = Intent(context, PrayerActionReceiver::class.java).apply {
                action = PrayerActionReceiver.ACTION_ALARM_DOOR
                putExtra("trigger_time", triggerTime)
            }
            val doorPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_DOOR,
                doorIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val doorFormatted = SimpleDateFormat("yyyy-MM-dd h:mm:ss a", Locale.US).format(Date(triggerTime))
            setAlarmClockAlarm(context, alarmManager, triggerTime, doorPendingIntent, "main-door")
            OvernightJournal.log(context, "SCHEDULER", "Scheduled main prayer door alarm for $doorFormatted")
        }
    }

    fun scheduleSnooze(context: Context, snoozeTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        if (snoozeTime <= now) return

        // Note: When snoozed, NO reminder notification is scheduled.
        // As soon as the snooze expires, the prayer door overlay immediately takes over the screen.
        val doorIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_ALARM_DOOR
            putExtra("trigger_time", snoozeTime)
        }
        val doorPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DOOR,
            doorIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val timeFormatted = SimpleDateFormat("yyyy-MM-dd h:mm:ss a", Locale.US).format(Date(snoozeTime))
        currentScheduledCycleId = snoozeTime
        setAlarmClockAlarm(context, alarmManager, snoozeTime, doorPendingIntent, "snooze-door")
        OvernightJournal.log(context, "SCHEDULER", "Scheduled snooze direct door alarm for $timeFormatted (no second reminder)")
    }

    private fun setAlarmClockAlarm(context: Context, alarmManager: AlarmManager, timeMillis: Long, pendingIntent: PendingIntent, label: String) {
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(timeMillis, pendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                OvernightJournal.log(context, "SCHEDULER", "setAlarmClock [$label] invoked API: setAlarmClock(RTC_WAKEUP, $timeMillis), canScheduleExactAlarms=$canScheduleExact")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                OvernightJournal.log(context, "SCHEDULER", "setExactAlarm [$label] fallback: setExactAndAllowWhileIdle(RTC_WAKEUP, $timeMillis), canScheduleExactAlarms=$canScheduleExact")
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                OvernightJournal.log(context, "SCHEDULER", "setExactAlarm [$label] fallback: setExact(RTC_WAKEUP, $timeMillis), canScheduleExactAlarms=$canScheduleExact")
            }
        } catch (e: SecurityException) {
            OvernightJournal.log(context, "SCHEDULER", "setAlarmClock [$label] SecurityException: ${e.message}. Falling back to inexact alarmManager.set(...) API, canScheduleExactAlarms=$canScheduleExact")
            alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
        }
    }

    private fun setExactAlarm(context: Context, alarmManager: AlarmManager, timeMillis: Long, pendingIntent: PendingIntent, label: String) {
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                OvernightJournal.log(context, "SCHEDULER", "setExactAlarm [$label] invoked API: setExactAndAllowWhileIdle(RTC_WAKEUP, $timeMillis), canScheduleExactAlarms=$canScheduleExact")
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                OvernightJournal.log(context, "SCHEDULER", "setExactAlarm [$label] invoked API: setExact(RTC_WAKEUP, $timeMillis), canScheduleExactAlarms=$canScheduleExact")
            }
        } catch (e: SecurityException) {
            OvernightJournal.log(context, "SCHEDULER", "setExactAlarm [$label] SecurityException: ${e.message}. Falling back to inexact alarmManager.set(...) API, canScheduleExactAlarms=$canScheduleExact")
            alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
        }
    }
}

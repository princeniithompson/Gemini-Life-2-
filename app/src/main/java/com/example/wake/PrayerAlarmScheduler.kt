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

    private const val REQUEST_CODE_REMINDER = 501
    private const val REQUEST_CODE_DOOR = 502

    fun scheduleNextPrayer(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val now = System.currentTimeMillis()
        val snoozeUntil = WakePrefsManager.getSnoozeUntil(context)
        val isSnoozed = snoozeUntil > now

        val triggerTime: Long = if (isSnoozed) {
            snoozeUntil
        } else {
            val (hour, minute) = WakePrefsManager.getPrayerHourMinute(context)
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            var prayerTime = cal.timeInMillis
            // If prayer time has passed today (by more than 30s), schedule for tomorrow
            if (now > prayerTime + 30_000L) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                prayerTime = cal.timeInMillis
            }
            prayerTime
        }

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

package com.example.wake

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WakeState(
    val lastScreenOff: Long = 0L,
    val lastPrayerCompleted: Long = 0L,
    val snoozeUntil: Long = 0L,
    val wouldTriggerNextUnlock: Boolean = false,
    val offHours: Double = 0.0,
    val sincePrayerHours: Double = 999.0,
    val useTestThresholds: Boolean = false,
    val overrideNextUnlock: Boolean = false,
    val screenOffRecorded: Boolean = false,
    val ritualPending: Boolean = false,
    val previewMissedDay: Boolean = false,
    val previewPrayNow: Boolean = false,
    val devNotificationTestEnabled: Boolean = false
)

object WakePrefsManager {
    private const val PREFS_NAME = "wake_detector_prefs"
    const val KEY_LAST_SCREEN_OFF_TIMESTAMP = "last_screen_off_timestamp"
    const val KEY_LAST_PRAYER_COMPLETED_TIMESTAMP = "last_prayer_completed_timestamp"
    const val KEY_SNOOZE_UNTIL_TIMESTAMP = "snooze_until_timestamp"
    const val KEY_TEST_OVERRIDE_NEXT_SCREEN_ON = "test_override_next_screen_on"
    const val KEY_USE_TEST_THRESHOLDS = "use_test_thresholds"
    const val KEY_SCREEN_OFF_RECORDED = "screen_off_recorded"
    const val KEY_PENDING_WAKE_TRIGGER = "pending_wake_trigger"
    const val KEY_PENDING_WAKE_TIMESTAMP = "pending_wake_timestamp"
    const val KEY_RITUAL_PENDING = "ritual_pending"
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_DOB = "user_dob"
    const val KEY_PRAYER_HISTORY = "prayer_history"
    const val KEY_FIRST_INSTALL_DATE = "first_install_date"
    const val KEY_PREVIEW_MISSED_DAY = "preview_missed_day"
    const val KEY_PREVIEW_PRAY_NOW = "preview_pray_now"
    const val KEY_DEV_NOTIFICATION_TEST_ENABLED = "dev_notification_test_enabled"
    const val KEY_PRAYER_TIME = "prayer_time"
    const val KEY_REMINDER_ENABLED = "reminder_enabled"
    const val KEY_SNOOZE_OPTIONS = "snooze_options"
    const val KEY_DEFAULT_SNOOZE_MINUTES = "default_snooze_minutes"
    const val KEY_LAST_REMINDER_TRIGGER_TIME = "last_reminder_trigger_time"
    const val KEY_LAST_DOOR_TRIGGER_TIME = "last_door_trigger_time"
    const val KEY_FIRST_RUN_PERMISSIONS_PROMPTED = "first_run_permissions_prompted"
    const val KEY_DOUBLE_TAP_END = "double_tap_end"
    const val KEY_INTERRUPTED_BY_CALL = "interrupted_by_call"

    fun getPrayerTime(context: Context): String {
        return getPrefs(context).getString(KEY_PRAYER_TIME, "06:00 AM") ?: "06:00 AM"
    }

    fun setPrayerTime(context: Context, time: String) {
        getPrefs(context).edit()
            .putString(KEY_PRAYER_TIME, time)
            .remove(KEY_LAST_REMINDER_TRIGGER_TIME)
            .remove(KEY_LAST_DOOR_TRIGGER_TIME)
            .remove(KEY_SNOOZE_UNTIL_TIMESTAMP)
            .apply()
        logWakeEvent("[SCHEDULER] Prayer time updated to $time (triggers reset)")
        WakeDetectorService.rescheduleTrigger(context)
        PrayerAlarmScheduler.scheduleNextPrayer(context)
    }

    fun getPrayerHourMinute(context: Context): Pair<Int, Int> {
        val timeStr = getPrayerTime(context)
        return parseTimeString(timeStr)
    }

    fun parseTimeString(timeStr: String): Pair<Int, Int> {
        return try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.US)
            val date = sdf.parse(timeStr)
            if (date != null) {
                val cal = java.util.Calendar.getInstance()
                cal.time = date
                Pair(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            } else {
                Pair(6, 0)
            }
        } catch (_: Exception) {
            try {
                val parts = timeStr.trim().split(" ")
                val timeParts = parts[0].split(":")
                var hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()
                if (parts.size > 1 && parts[1].equals("PM", ignoreCase = true) && hour < 12) {
                    hour += 12
                } else if (parts.size > 1 && parts[1].equals("AM", ignoreCase = true) && hour == 12) {
                    hour = 0
                }
                Pair(hour, minute)
            } catch (_: Exception) {
                Pair(6, 0)
            }
        }
    }

    fun isReminderEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REMINDER_ENABLED, true)
    }

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    fun getSnoozeOptions(context: Context): List<Int> {
        val raw = getPrefs(context).getString(KEY_SNOOZE_OPTIONS, "15,30,60") ?: "15,30,60"
        return try {
            raw.split(",").map { it.trim().toInt() }
        } catch (_: Exception) {
            listOf(15, 30, 60)
        }
    }

    fun setSnoozeOptions(context: Context, options: List<Int>) {
        val raw = options.joinToString(",")
        getPrefs(context).edit().putString(KEY_SNOOZE_OPTIONS, raw).apply()
    }

    fun formatDuration(minutes: Int): String {
        return when (minutes) {
            60 -> "1 hour"
            90 -> "1.5 hours"
            120 -> "2 hours"
            else -> "$minutes min"
        }
    }

    fun getSnoozeOptionsSummary(context: Context): String {
        val options = getSnoozeOptions(context)
        return options.joinToString(", ") { formatDuration(it) }
    }

    fun getDefaultSnoozeMinutes(context: Context): Int {
        val options = getSnoozeOptions(context)
        val defaultVal = if (options.isNotEmpty()) options.first() else 15
        return getPrefs(context).getInt(KEY_DEFAULT_SNOOZE_MINUTES, defaultVal)
    }

    fun setDefaultSnoozeMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_DEFAULT_SNOOZE_MINUTES, minutes).apply()
    }

    fun getLastReminderTriggerTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_REMINDER_TRIGGER_TIME, 0L)
    }

    fun setLastReminderTriggerTime(context: Context, ts: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_REMINDER_TRIGGER_TIME, ts).apply()
    }

    fun getLastDoorTriggerTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_DOOR_TRIGGER_TIME, 0L)
    }

    fun setLastDoorTriggerTime(context: Context, ts: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_DOOR_TRIGGER_TIME, ts).apply()
    }

    fun getPreviewMissedDay(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PREVIEW_MISSED_DAY, false)
    }

    fun setPreviewMissedDay(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PREVIEW_MISSED_DAY, enabled).apply()
        updateWakeState(context)
    }

    fun getPreviewPrayNow(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PREVIEW_PRAY_NOW, false)
    }

    fun setPreviewPrayNow(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PREVIEW_PRAY_NOW, enabled).apply()
        updateWakeState(context)
    }

    private val _wakeEvents = MutableStateFlow<List<String>>(emptyList())
    val wakeEvents: StateFlow<List<String>> = _wakeEvents.asStateFlow()

    private val _wakeState = MutableStateFlow(WakeState())
    val wakeState: StateFlow<WakeState> = _wakeState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCustomApiKey(context: Context): String {
        return getPrefs(context).getString("custom_api_key", "") ?: ""
    }

    fun setCustomApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString("custom_api_key", key).apply()
    }

    fun isOnboardingComplete(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    fun setOnboardingComplete(context: Context, complete: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
    }

    fun hasPromptedFirstRunPermissions(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FIRST_RUN_PERMISSIONS_PROMPTED, false)
    }

    fun setPromptedFirstRunPermissions(context: Context, prompted: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_RUN_PERMISSIONS_PROMPTED, prompted).apply()
    }

    fun getUserName(context: Context): String {
        return getPrefs(context).getString(KEY_USER_NAME, "") ?: ""
    }

    fun setUserName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserDob(context: Context): String {
        return getPrefs(context).getString(KEY_USER_DOB, "") ?: ""
    }

    fun setUserDob(context: Context, dob: String) {
        getPrefs(context).edit().putString(KEY_USER_DOB, dob).apply()
    }

    fun isScreenOffRecorded(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SCREEN_OFF_RECORDED, false)
    }

    fun setScreenOffRecorded(context: Context, recorded: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SCREEN_OFF_RECORDED, recorded).apply()
        updateWakeState(context)
    }

    fun getLastScreenOff(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_SCREEN_OFF_TIMESTAMP, 0L)
    }

    fun setLastScreenOff(context: Context, timestamp: Long) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_SCREEN_OFF_TIMESTAMP, timestamp)
            .putBoolean(KEY_SCREEN_OFF_RECORDED, true)
            .apply()
        updateWakeState(context)
    }

    fun getLastPrayerCompleted(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_PRAYER_COMPLETED_TIMESTAMP, 0L)
    }

    fun isPrayerCompletedToday(context: Context): Boolean {
        val lastCompleted = getLastPrayerCompleted(context)
        if (lastCompleted <= 0L) return false
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val completedDate = sdf.format(Date(lastCompleted))
        val todayDate = sdf.format(Date())
        return completedDate == todayDate
    }

    fun setLastPrayerCompleted(context: Context, timestamp: Long) {
        val prefs = getPrefs(context)
        prefs.edit().putLong(KEY_LAST_PRAYER_COMPLETED_TIMESTAMP, timestamp).apply()

        val ts = if (timestamp > 0) timestamp else System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayIso = sdf.format(Date(ts))
        val history = (prefs.getStringSet(KEY_PRAYER_HISTORY, emptySet()) ?: emptySet()).toMutableSet()
        history.add(todayIso)
        prefs.edit().putStringSet(KEY_PRAYER_HISTORY, history).apply()

        updateWakeState(context)
    }

    fun getPrayerHistory(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_PRAYER_HISTORY, emptySet()) ?: emptySet()
    }

    fun getFirstInstallDate(context: Context): String {
        val prefs = getPrefs(context)
        var installDate = prefs.getString(KEY_FIRST_INSTALL_DATE, null)
        if (installDate.isNullOrEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            installDate = sdf.format(Date())
            prefs.edit().putString(KEY_FIRST_INSTALL_DATE, installDate).apply()
        }
        return installDate
    }

    fun calculateStreak(history: Set<String>): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = java.util.Calendar.getInstance()
        var todayIso = sdf.format(cal.time)

        var streak = 0
        if (history.contains(todayIso)) {
            while (history.contains(todayIso)) {
                streak++
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                todayIso = sdf.format(cal.time)
            }
        } else {
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            var yesterdayIso = sdf.format(cal.time)
            while (history.contains(yesterdayIso)) {
                streak++
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                yesterdayIso = sdf.format(cal.time)
            }
        }
        return streak
    }

    fun getSnoozeUntil(context: Context): Long {
        return getPrefs(context).getLong(KEY_SNOOZE_UNTIL_TIMESTAMP, 0L)
    }

    fun setSnoozeUntil(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_SNOOZE_UNTIL_TIMESTAMP, timestamp).apply()
        updateWakeState(context)
    }

    fun getTestOverrideNextScreenOn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TEST_OVERRIDE_NEXT_SCREEN_ON, false)
    }

    fun setTestOverrideNextScreenOn(context: Context, override: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TEST_OVERRIDE_NEXT_SCREEN_ON, override).apply()
        updateWakeState(context)
    }

    fun getUseTestThresholds(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_TEST_THRESHOLDS, false)
    }

    fun setUseTestThresholds(context: Context, useTest: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_TEST_THRESHOLDS, useTest).apply()
        val modeStr = if (useTest) "TEST (1m off / 5m cooldown)" else "REAL (4h off / 12h cooldown)"
        logWakeEvent("[WAKE-TEST] Threshold mode set to $modeStr")
        updateWakeState(context)
    }

    fun injectFakeSleep(context: Context) {
        val now = System.currentTimeMillis()
        val twentyHoursAgo = now - (20 * 3600000L)
        getPrefs(context).edit()
            .putLong(KEY_LAST_PRAYER_COMPLETED_TIMESTAMP, twentyHoursAgo)
            .putBoolean(KEY_TEST_OVERRIDE_NEXT_SCREEN_ON, true)
            .putBoolean(KEY_RITUAL_PENDING, true)
            .apply()
        val msg = "[WAKE-TEST] Fake sleep injected (off=5h, sincePrayer=20h)"
        logWakeEvent(msg)
        updateWakeState(context)
    }

    fun isRitualPending(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_RITUAL_PENDING, false)
    }

    fun setRitualPending(context: Context, pending: Boolean, reason: String = "unspecified") {
        getPrefs(context).edit().putBoolean(KEY_RITUAL_PENDING, pending).apply()
        val action = if (pending) "kept" else "cleared"
        val msg = "[DOOR] ritual pending $action reason=$reason"
        Log.i("WakeDetector", msg)
        logWakeEvent(msg)
        OvernightJournal.log(context, "SCHEDULER", msg)
        updateWakeState(context)
    }

    fun setPendingWakeTrigger(context: Context, pending: Boolean, timestamp: Long = System.currentTimeMillis()) {
        getPrefs(context).edit()
            .putBoolean(KEY_PENDING_WAKE_TRIGGER, pending)
            .putLong(KEY_PENDING_WAKE_TIMESTAMP, timestamp)
            .apply()
    }

    fun isPendingWakeTrigger(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PENDING_WAKE_TRIGGER, false)
    }

    fun getPendingWakeTriggerTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_PENDING_WAKE_TIMESTAMP, 0L)
    }

    fun clearPendingWakeTrigger(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_PENDING_WAKE_TRIGGER)
            .remove(KEY_PENDING_WAKE_TIMESTAMP)
            .apply()
    }

    fun isDevNotificationTestEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DEV_NOTIFICATION_TEST_ENABLED, false)
    }

    fun setDevNotificationTestEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DEV_NOTIFICATION_TEST_ENABLED, enabled).apply()
        val msg = "[DEV] Notification test button in Profile: ${if (enabled) "ENABLED" else "DISABLED"}"
        logWakeEvent(msg)
        updateWakeState(context)
    }

    fun resetWakeData(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_LAST_SCREEN_OFF_TIMESTAMP)
            .remove(KEY_LAST_PRAYER_COMPLETED_TIMESTAMP)
            .remove(KEY_SNOOZE_UNTIL_TIMESTAMP)
            .remove(KEY_TEST_OVERRIDE_NEXT_SCREEN_ON)
            .remove(KEY_USE_TEST_THRESHOLDS)
            .remove(KEY_SCREEN_OFF_RECORDED)
            .remove(KEY_PENDING_WAKE_TRIGGER)
            .remove(KEY_PENDING_WAKE_TIMESTAMP)
            .remove(KEY_RITUAL_PENDING)
            .apply()
        val msg = "[WAKE-TEST] Wake data reset"
        logWakeEvent(msg)
        updateWakeState(context)
    }

    fun isDoubleTapEndEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DOUBLE_TAP_END, true)
    }

    fun setDoubleTapEndEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DOUBLE_TAP_END, enabled).apply()
    }

    fun isInterruptedByCall(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_INTERRUPTED_BY_CALL, false)
    }

    fun setInterruptedByCall(context: Context, interrupted: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_INTERRUPTED_BY_CALL, interrupted).apply()
        val msg = "[CALL] interrupted_by_call set to $interrupted"
        Log.i("WakeDetector", msg)
        logWakeEvent(msg)
    }

    fun logWakeEvent(event: String) {
        val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = "$timeString $event"
        _wakeEvents.value = (_wakeEvents.value + formatted).takeLast(100)
    }

    fun updateWakeState(context: Context) {
        val lastScreenOff = getLastScreenOff(context)
        val lastPrayer = getLastPrayerCompleted(context)
        val snooze = getSnoozeUntil(context)
        val testOverride = getTestOverrideNextScreenOn(context)
        val useTestThresholds = getUseTestThresholds(context)
        val recorded = isScreenOffRecorded(context)
        val ritualPending = isRitualPending(context)
        val now = System.currentTimeMillis()

        val offHours = if (lastScreenOff > 0) (now - lastScreenOff) / 3600000.0 else 0.0
        val sincePrayerHours = if (lastPrayer > 0) (now - lastPrayer) / 3600000.0 else 999.0

        val minOffHours = if (useTestThresholds) (1.0 / 60.0) else 4.0
        val minPrayerHours = if (useTestThresholds) (5.0 / 60.0) else 12.0

        val offHoursValid = if (testOverride) true else (recorded && offHours >= minOffHours)
        val sincePrayerValid = if (testOverride) true else (lastPrayer == 0L || sincePrayerHours >= minPrayerHours)
        val snoozeClear = now >= snooze

        val wouldTrigger = offHoursValid && sincePrayerValid && snoozeClear

        val previewMissedDay = getPreviewMissedDay(context)
        val previewPrayNow = getPreviewPrayNow(context)
        val devNotificationTestEnabled = isDevNotificationTestEnabled(context)

        _wakeState.value = WakeState(
            lastScreenOff = lastScreenOff,
            lastPrayerCompleted = lastPrayer,
            snoozeUntil = snooze,
            wouldTriggerNextUnlock = wouldTrigger,
            offHours = if (testOverride) 5.0 else offHours,
            sincePrayerHours = if (testOverride && lastPrayer == 0L) 20.0 else sincePrayerHours,
            useTestThresholds = useTestThresholds,
            overrideNextUnlock = testOverride,
            screenOffRecorded = recorded,
            ritualPending = ritualPending,
            previewMissedDay = previewMissedDay,
            previewPrayNow = previewPrayNow,
            devNotificationTestEnabled = devNotificationTestEnabled
        )
    }
}

package com.example.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val msg = "[WAKE] Boot or package replaced received, scheduling next prayer alarm"
            Log.i("WakeDetector", msg)
            WakePrefsManager.logWakeEvent(msg)
            // On-demand: Reschedule exact hardware alarm without starting persistent background service
            PrayerAlarmScheduler.scheduleNextPrayer(context)

            // Belt and braces: if prayer was deferred offline and today still matches, re-enqueue WorkManager and cutoff
            if (WakePrefsManager.isPrayerDeferredOffline(context)) {
                val deferredDay = WakePrefsManager.getDeferredDay(context)
                val today = WakePrefsManager.getTodayString()
                if (deferredDay.isNotBlank() && deferredDay == today) {
                    DeferredResumeWorker.enqueue(context)
                    PrayerAlarmScheduler.scheduleMidnightCutoff(context)
                    Log.i("WakeDetector", "[BOOT] Re-enqueued WorkManager request and midnight cutoff for deferred day $deferredDay")
                } else if (deferredDay.isNotBlank() && deferredDay != today) {
                    WakePrefsManager.clearDeferredPrayer(context)
                }
            }
        }
    }
}

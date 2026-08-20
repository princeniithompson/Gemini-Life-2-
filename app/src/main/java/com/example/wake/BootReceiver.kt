package com.example.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val msg = "[WAKE] Boot or package replaced received, starting WakeDetectorService"
            Log.i("WakeDetector", msg)
            WakePrefsManager.logWakeEvent(msg)
            WakeDetectorService.startService(context)
            PrayerAlarmScheduler.scheduleNextPrayer(context)
        }
    }
}

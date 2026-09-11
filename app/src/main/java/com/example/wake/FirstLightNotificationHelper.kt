package com.example.wake

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FirstLightNotificationHelper {

    const val NOTIFICATION_ID = WakeDetectorService.ALARM_NOTIFICATION_ID
    const val STUBBORN_MEDIA_NOTIFICATION_ID = 1005

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val existing = nm.getNotificationChannel(WakeDetectorService.ALARM_CHANNEL_ID)
            if (existing == null || existing.sound == null) {
                if (existing != null) {
                    try { nm.deleteNotificationChannel(WakeDetectorService.ALARM_CHANNEL_ID) } catch (_: Exception) {}
                }
                val alarmChannel = NotificationChannel(
                    WakeDetectorService.ALARM_CHANNEL_ID,
                    "Morning Prayer Alarm",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Morning prayer alarm and reminder notifications"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 1000, 500)
                    enableLights(true)
                    lightColor = android.graphics.Color.parseColor("#B4574E")
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                    setSound(soundUri, audioAttributes)
                }
                nm.createNotificationChannel(alarmChannel)
            }
        }
    }

    fun isNotificationEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * State 1: 30-Second Alarm Clock Notification
     * Features:
     * - CATEGORY_ALARM, PRIORITY_MAX, setOngoing(true), setFullScreenIntent for sticky banner
     * - 1-Click Snooze: [ ✕ Snooze (15m) ] -> Instant snooze in 1 tap
     * - [ ⏱ More ] -> Custom durations (30m, 1h)
     * - [ ✓ Proceed ] -> Immediately launches prayer door
     * - Swipe to stop via setDeleteIntent
     */
    fun postState1Reminder(context: Context): Boolean {
        ensureChannel(context)
        val titleText = "First Light — Morning Prayer"
        val bodyText = "Prayer starts in 30 seconds"

        val defaultSnoozeMin = WakePrefsManager.getDefaultSnoozeMinutes(context)
        val defaultSnoozeLabel = WakePrefsManager.formatDuration(defaultSnoozeMin)

        // Action: ✓ Proceed (Immediately launches prayer door overlay without launching MainActivity)
        val proceedIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_NOTIF_PROCEED
        }
        val proceedPendingIntent = PendingIntent.getBroadcast(
            context,
            202,
            proceedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: ✕ Snooze (Transitions to snooze menu: 15m, 30m, 1h)
        val snoozeIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_NOTIF_SNOOZE_MENU
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            201,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Swipe to stop / skip
        val skipIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_SKIP
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            203,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, WakeDetectorService.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_streak_flame)
            .setColor(0xFFB4574E.toInt())
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(alarmSound, android.media.AudioManager.STREAM_ALARM)
            .setFullScreenIntent(proceedPendingIntent, true)
            .setDeleteIntent(skipPendingIntent)
            .setContentIntent(proceedPendingIntent)
            .setOngoing(true) // Persistent Alarm style: stays sticky on screen
            .setAutoCancel(false)
            .addAction(R.drawable.ic_streak_flame, "✕ Snooze", snoozePendingIntent)
            .addAction(R.drawable.ic_streak_flame, "✓ Proceed", proceedPendingIntent)
            .build()

        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
            Log.i("WakeDetector", "[NOTIF] Posted Sticky Alarm Notification (ID=$NOTIFICATION_ID)")
            true
        } catch (e: Exception) {
            Log.e("WakeDetector", "[NOTIF] Failed to post Alarm notification: ${e.message}", e)
            false
        }
    }

    /**
     * State 2: Extended Snooze Options Menu
     * Displays all available durations [ 15 min ], [ 30 min ], [ 1 hour ] with alarm prominence.
     */
    fun postState2SnoozeOptions(context: Context): Boolean {
        ensureChannel(context)
        val options = WakePrefsManager.getSnoozeOptions(context).let {
            if (it.size >= 3) it else listOf(15, 30, 60)
        }

        val titleText = "First Light — Snooze Prayer"
        val bodyText = "Choose snooze duration:"

        val proceedIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_NOTIF_PROCEED
        }
        val proceedPendingIntent = PendingIntent.getBroadcast(
            context,
            399,
            proceedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, WakeDetectorService.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_streak_flame)
            .setColor(0xFFB4574E.toInt())
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(proceedPendingIntent, true)
            .setDeleteIntent(proceedPendingIntent)
            .setContentIntent(proceedPendingIntent)
            .setOngoing(true) // Stays open while choosing
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)

        // Add action buttons for durations
        options.take(3).forEachIndexed { index, durationMin ->
            val durationLabel = WakePrefsManager.formatDuration(durationMin)
            val intent = Intent(context, PrayerActionReceiver::class.java).apply {
                action = PrayerActionReceiver.ACTION_NOTIF_SNOOZE_SELECT
                putExtra(PrayerActionReceiver.EXTRA_SNOOZE_MINUTES, durationMin)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                300 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, durationLabel, pendingIntent)
        }

        // Action: Proceed Now
        builder.addAction(0, "✓ Proceed", proceedPendingIntent)

        val notification = builder.build()

        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
            Log.i("WakeDetector", "[NOTIF] Posted State 2 Snooze Options notification (ID=$NOTIFICATION_ID)")
            true
        } catch (e: Exception) {
            Log.e("WakeDetector", "[NOTIF] Failed to post State 2 notification: ${e.message}", e)
            false
        }
    }

    /**
     * State 3: Snooze Confirmation Notification
     */
    fun postState3Confirmation(context: Context, snoozeUntilMillis: Long): Boolean {
        ensureChannel(context)
        val timeFormatted = SimpleDateFormat("h:mm a", Locale.US).format(Date(snoozeUntilMillis))
        val titleText = "First Light — Prayer Snoozed"
        val bodyText = "Snoozed. We'll gently remind you at $timeFormatted."

        val dismissIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_NOTIF_DISMISS
        }
        val contentPendingIntent = PendingIntent.getBroadcast(
            context,
            200,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, WakeDetectorService.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_streak_flame)
            .setColor(0xFF3E8E58.toInt())
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        val success = try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
            Log.i("WakeDetector", "[NOTIF] Posted State 3 confirmation notification (ID=$NOTIFICATION_ID)")
            true
        } catch (e: Exception) {
            Log.e("WakeDetector", "[NOTIF] Failed to post State 3 notification: ${e.message}", e)
            false
        }

        // Auto-cancel confirmation after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
        }, 5000L)

        return success
    }

    fun cancelNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    /**
     * Wake-Level Offline Notification
     * Same importance, sound, and DND-bypass as the morning alarm notification.
     * Posted when scheduled prayer arrives but the device has no internet connection.
     */
    fun postOfflineNotification(context: Context): Boolean {
        ensureChannel(context)
        val titleText = "First Light — Morning Prayer"
        val bodyText = "No internet. Prayer waits for you online."

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            204,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, WakeDetectorService.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_streak_flame)
            .setColor(0xFFB4574E.toInt())
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(alarmSound, android.media.AudioManager.STREAM_ALARM)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
            Log.i("WakeDetector", "[NOTIF] Posted wake-level offline notification: '$bodyText' (ID=$NOTIFICATION_ID)")
            true
        } catch (e: Exception) {
            Log.e("WakeDetector", "[NOTIF] Failed to post offline notification: ${e.message}", e)
            false
        }
    }

    /**
     * Stubborn Media Heads-Up Notification (One-Time, Auto-Dismiss ~5s)
     */
    fun postStubbornMediaNotification(context: Context): Boolean {
        ensureChannel(context)
        val titleText = "First Light"
        val bodyText = "Another app is playing sound. Pause it to hear your prayer clearly."

        val notification = NotificationCompat.Builder(context, WakeDetectorService.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_streak_flame)
            .setColor(0xFFB4574E.toInt())
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        val success = try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(STUBBORN_MEDIA_NOTIFICATION_ID, notification)
            Log.i("WakeDetector", "[NOTIF] Posted stubborn media notification (ID=$STUBBORN_MEDIA_NOTIFICATION_ID)")
            true
        } catch (e: Exception) {
            Log.e("WakeDetector", "[NOTIF] Failed to post stubborn media notification: ${e.message}", e)
            false
        }

        // Auto-dismiss after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(STUBBORN_MEDIA_NOTIFICATION_ID)
            } catch (_: Exception) {}
        }, 5000L)

        return success
    }

    fun cancelStubbornMediaNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(STUBBORN_MEDIA_NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}

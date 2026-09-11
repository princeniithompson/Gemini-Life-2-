package com.example.wake

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.api.ApiKeyProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DeferredResumeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i("WakeDetector", "[RESUME_WORKER] DeferredResumeWorker awakened by WorkManager network trigger")
        OvernightJournal.log(applicationContext, "RESUME", "DeferredResumeWorker awakened by network trigger")

        if (!WakePrefsManager.isPrayerDeferredOffline(applicationContext)) {
            Log.i("WakeDetector", "[RESUME_WORKER] Prayer is not deferred, completing worker successfully")
            return Result.success()
        }

        val deferredDay = WakePrefsManager.getDeferredDay(applicationContext)
        val today = WakePrefsManager.getTodayString()
        if (deferredDay.isNotBlank() && deferredDay != today) {
            Log.i("WakeDetector", "[RESUME_WORKER] Defer day ($deferredDay) != today ($today), window expired, cleaning up")
            OvernightJournal.log(applicationContext, "RESUME", "Defer day ($deferredDay) != today ($today), window expired")
            WakePrefsManager.clearDeferredPrayer(applicationContext)
            FirstLightNotificationHelper.cancelNotification(applicationContext)
            return Result.success()
        }

        // 1. Instant validated-internet check
        val isSystemOnline = ConnectivityChecker.hasValidatedInternet(applicationContext)
        if (!isSystemOnline) {
            Log.i("WakeDetector", "[RESUME_WORKER] System reports offline -> retrying")
            OvernightJournal.log(applicationContext, "RESUME", "System reports offline -> retrying")
            return Result.retry()
        }

        // 2. ONE bounded Gemini ping (3s timeout) to verify real cloud egress past captive portals
        val pingSuccess = performGeminiPing(applicationContext)
        if (!pingSuccess) {
            Log.i("WakeDetector", "[RESUME_WORKER] Bounded Gemini ping failed (captive portal or cloud unreachable) -> retrying in 30s")
            OvernightJournal.log(applicationContext, "RESUME", "Gemini ping failed (captive portal or unreachable) -> retrying")
            return Result.retry()
        }

        Log.i("WakeDetector", "[RESUME_WORKER] Internet validated and Gemini reachable -> scheduling resume cooldown")
        OvernightJournal.log(applicationContext, "RESUME", "Internet validated and Gemini reachable -> scheduling resume cooldown")
        PrayerAlarmScheduler.scheduleResumeCooldown(applicationContext)
        return Result.success()
    }

    private fun performGeminiPing(context: Context): Boolean {
        return try {
            val key = ApiKeyProvider.getApiKey(context)
            val url = if (key.isNotBlank()) {
                "https://generativelanguage.googleapis.com/v1beta/models?key=$key"
            } else {
                "https://generativelanguage.googleapis.com"
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                // Response 200..499 indicates the Google backend was reached (not an SSL interception or timeout)
                val reached = response.code in 200..499
                Log.i("WakeDetector", "[RESUME_WORKER] Gemini ping HTTP ${response.code}, reached=$reached")
                reached
            }
        } catch (e: Exception) {
            Log.w("WakeDetector", "[RESUME_WORKER] Gemini ping exception: ${e.message}")
            false
        }
    }

    companion object {
        const val WORK_NAME = "fl_deferred_resume"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<DeferredResumeWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
            Log.i("WakeDetector", "[RESUME_WORKER] Enqueued unique WorkManager request '$WORK_NAME' (KEEP, NetworkType.CONNECTED)")
            OvernightJournal.log(context, "RESUME", "Enqueued WorkManager request '$WORK_NAME'")
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                Log.i("WakeDetector", "[RESUME_WORKER] Cancelled WorkManager request '$WORK_NAME'")
                OvernightJournal.log(context, "RESUME", "Cancelled WorkManager request '$WORK_NAME'")
            } catch (e: Exception) {
                Log.w("WakeDetector", "[RESUME_WORKER] Error cancelling WorkManager request: ${e.message}")
            }
        }
    }
}

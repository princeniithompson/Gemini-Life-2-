package com.example.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ApiKeyProvider {
    private const val PREFS_NAME = "wake_detector_prefs"
    private const val KEY_USER_API_KEY = "custom_api_key"
    private const val KEY_DEV_BACKDOOR_ENABLED = "dev_backdoor_enabled"

    private val _authErrorEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authErrorEvent: SharedFlow<Unit> = _authErrorEvent.asSharedFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getUserApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_USER_API_KEY, "")?.trim() ?: ""
    }

    fun setUserApiKey(context: Context, key: String) {
        val cleanKey = key.trim()
        getPrefs(context).edit().putString(KEY_USER_API_KEY, cleanKey).apply()
        Log.i("ApiKeyProvider", "[KEY] User API key saved (length=${cleanKey.length})")
    }

    fun clearUserApiKey(context: Context) {
        getPrefs(context).edit().remove(KEY_USER_API_KEY).apply()
        Log.i("ApiKeyProvider", "[KEY] User API key cleared")
    }

    fun isDeveloperMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DEV_BACKDOOR_ENABLED, false)
    }

    fun setDeveloperMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DEV_BACKDOOR_ENABLED, enabled).apply()
        Log.i("ApiKeyProvider", "[DEV] Developer backdoor key enabled: $enabled")
    }

    fun hasBuildConfigKey(): Boolean {
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }
        return buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY" && !buildConfigKey.contains("YOUR_API_KEY")
    }

    fun getKeySource(context: Context): String {
        val userKey = getUserApiKey(context)
        if (userKey.isNotBlank()) return "User Key"
        if (isDeveloperMode(context) && hasBuildConfigKey()) return "Developer Backdoor"
        return "None"
    }

    /**
     * Effective key resolution:
     * Stored user key if not blank,
     * ELSE backdoor key (BuildConfig.GEMINI_API_KEY) IF developer mode is active,
     * ELSE nothing ("").
     */
    fun getApiKey(context: Context): String {
        val userKey = getUserApiKey(context)
        if (userKey.isNotBlank()) {
            return userKey
        }
        if (isDeveloperMode(context)) {
            val buildConfigKey = try {
                BuildConfig.GEMINI_API_KEY
            } catch (_: Exception) {
                ""
            }
            if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY" && !buildConfigKey.contains("YOUR_API_KEY")) {
                return buildConfigKey
            }
        }
        return ""
    }

    fun hasWorkingKey(context: Context): Boolean {
        return getApiKey(context).isNotBlank()
    }

    fun isKeyValidFormat(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || trimmed.contains(" ") || trimmed.length < 20) {
            return false
        }
        return true
    }

    fun notifyAuthError() {
        Log.w("ApiKeyProvider", "[AUTH] Auth error detected from Gemini service")
        _authErrorEvent.tryEmit(Unit)
    }

    /**
     * Live test ping against Gemini REST endpoint (list models).
     * Returns Result.success(Unit) if authenticated, Result.failure(Exception) if unauthorized or invalid.
     */
    suspend fun validateKeyLive(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (!isKeyValidFormat(cleanKey)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid key format"))
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.example.favorites

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object FavoritesManager {
    private const val PREFS_NAME = "first_light_favorites"
    private const val KEY_SAVED_PRAYERS_JSON = "saved_prayers_json"

    fun getSavedPrayers(context: Context): List<SavedPrayer> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_SAVED_PRAYERS_JSON, null) ?: return emptyList()
        val list = mutableListOf<SavedPrayer>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val date = obj.optLong("date", System.currentTimeMillis())
                val memoryVerse = obj.optString("memoryVerse", "")
                val transcript = obj.optString("transcript", "")
                list.add(SavedPrayer(id, date, memoryVerse, transcript))
            }
        } catch (e: Exception) {
            Log.e("FavoritesManager", "[FAVORITES] Error parsing saved prayers JSON", e)
        }
        return list
    }

    fun savePrayer(context: Context, memoryVerse: String, transcript: String): SavedPrayer {
        val prayer = SavedPrayer(
            id = UUID.randomUUID().toString(),
            date = System.currentTimeMillis(),
            memoryVerse = memoryVerse,
            transcript = transcript
        )
        val currentList = getSavedPrayers(context).toMutableList()
        currentList.add(0, prayer)
        saveList(context, currentList)
        Log.i("FavoritesManager", "[FAVORITES] Saved to prefs. Total count: ${currentList.size}")
        return prayer
    }

    fun deletePrayer(context: Context, id: String) {
        val currentList = getSavedPrayers(context).filterNot { it.id == id }
        saveList(context, currentList)
    }

    fun isSaved(context: Context, id: String): Boolean {
        return getSavedPrayers(context).any { it.id == id }
    }

    private fun saveList(context: Context, list: List<SavedPrayer>) {
        val jsonArray = JSONArray()
        for (prayer in list) {
            val obj = JSONObject().apply {
                put("id", prayer.id)
                put("date", prayer.date)
                put("memoryVerse", prayer.memoryVerse)
                put("transcript", prayer.transcript)
            }
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SAVED_PRAYERS_JSON, jsonArray.toString()).apply()
    }
}

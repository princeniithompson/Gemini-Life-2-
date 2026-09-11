package com.example.wake

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

object ConnectivityChecker {

    /**
     * Instant system-state query for NET_CAPABILITY_INTERNET and NET_CAPABILITY_VALIDATED.
     * Main-thread safe, NO network ping, NO sockets.
     */
    fun hasValidatedInternet(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                Log.i("WakeDetector", "[CONNECTIVITY] ConnectivityManager unavailable -> OFFLINE")
                OvernightJournal.log(context, "CONNECTIVITY", "ConnectivityManager unavailable -> OFFLINE")
                return false
            }

            val activeNetwork = cm.activeNetwork
            if (activeNetwork == null) {
                Log.i("WakeDetector", "[CONNECTIVITY] activeNetwork is null -> OFFLINE")
                OvernightJournal.log(context, "CONNECTIVITY", "activeNetwork is null -> OFFLINE")
                return false
            }

            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            if (capabilities == null) {
                Log.i("WakeDetector", "[CONNECTIVITY] NetworkCapabilities is null -> OFFLINE")
                OvernightJournal.log(context, "CONNECTIVITY", "NetworkCapabilities is null -> OFFLINE")
                return false
            }

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val online = hasInternet && isValidated

            val statusStr = if (online) "ONLINE" else "OFFLINE"
            val infoMsg = "[CONNECTIVITY] Result: $statusStr (internet=$hasInternet, validated=$isValidated)"
            Log.i("WakeDetector", infoMsg)
            OvernightJournal.log(context, "CONNECTIVITY", infoMsg)

            online
        } catch (e: Exception) {
            val errMsg = "[CONNECTIVITY] Exception querying network state: ${e.message} -> OFFLINE"
            Log.i("WakeDetector", errMsg)
            OvernightJournal.log(context, "CONNECTIVITY", errMsg)
            false
        }
    }

    fun isConnected(context: Context): Boolean = hasValidatedInternet(context)
}

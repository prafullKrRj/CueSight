package com.cuegight.cuesight.core.network

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Manages WiFi connection to ESP32_CAM
 * Handles both Android 10+ (WifiNetworkSuggestion) and Android 9- (direct connection)
 */
class ConnectionManager(private val context: Context) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val ESP32_SSID = "ESP32_CAM"
        private const val ESP32_PASSWORD = "12345678"
    }

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Connect to ESP32_CAM WiFi network
     * Uses different methods based on Android version
     */
    suspend fun connectToESP32(): ConnectionResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectAndroid10Plus()
        } else {
            connectAndroid9Minus()
        }
    }

    /**
     * Android 10+ connection using WifiNetworkSuggestion
     * Requires user approval through WiFi settings
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectAndroid10Plus(): ConnectionResult {
        try {
            Log.d(TAG, "📱 Using Android 10+ connection method")

            // Create network suggestion
            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(ESP32_SSID)
                .setWpa2Passphrase(ESP32_PASSWORD)
                .build()

            // Add suggestion
            val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
            
            return when (status) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> {
                    Log.d(TAG, "✅ Network suggestion added successfully")
                    // User needs to manually connect through WiFi settings
                    ConnectionResult.NeedsUserAction("Please connect to ESP32_CAM WiFi in settings")
                }
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> {
                    Log.d(TAG, "ℹ️ Network suggestion already exists")
                    ConnectionResult.NeedsUserAction("Please connect to ESP32_CAM WiFi in settings")
                }
                else -> {
                    Log.e(TAG, "❌ Failed to add network suggestion: $status")
                    ConnectionResult.Error("Failed to add network suggestion")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding network suggestion: ${e.message}", e)
            return ConnectionResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Android 9- connection using direct WifiConfiguration
     * Can connect automatically without user interaction
     */
    @Suppress("DEPRECATION")
    private fun connectAndroid9Minus(): ConnectionResult {
        try {
            Log.d(TAG, "📱 Using Android 9- connection method")

            // Create WiFi configuration
            val config = WifiConfiguration().apply {
                SSID = "\"$ESP32_SSID\""
                preSharedKey = "\"$ESP32_PASSWORD\""
            }

            // Add network and connect
            val networkId = wifiManager.addNetwork(config)
            if (networkId == -1) {
                Log.e(TAG, "❌ Failed to add network configuration")
                return ConnectionResult.Error("Failed to add network")
            }

            val disconnected = wifiManager.disconnect()
            if (!disconnected) {
                Log.w(TAG, "⚠️ Failed to disconnect from current network")
            }

            val enabled = wifiManager.enableNetwork(networkId, true)
            if (!enabled) {
                Log.e(TAG, "❌ Failed to enable network")
                return ConnectionResult.Error("Failed to enable network")
            }

            val reconnected = wifiManager.reconnect()
            if (!reconnected) {
                Log.e(TAG, "❌ Failed to reconnect")
                return ConnectionResult.Error("Failed to reconnect")
            }

            Log.d(TAG, "✅ WiFi connection initiated")
            return ConnectionResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error connecting to WiFi: ${e.message}", e)
            return ConnectionResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Open WiFi settings for user to manually connect
     */
    fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Check if currently connected to ESP32_CAM
     */
    fun isConnectedToESP32(): Boolean {
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid.removeSurrounding("\"")
        val isConnected = ssid == ESP32_SSID
        
        Log.d(TAG, "WiFi Status: SSID=$ssid, Connected to ESP32=$isConnected")
        return isConnected
    }

    /**
     * Get current WiFi SSID
     */
    fun getCurrentSSID(): String? {
        val wifiInfo = wifiManager.connectionInfo
        return wifiInfo.ssid?.removeSurrounding("\"")
    }

    /**
     * Check if WiFi is enabled
     */
    fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }

    /**
     * Request to enable WiFi (opens settings on Android 10+)
     */
    @Suppress("DEPRECATION")
    fun enableWifi(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ requires user to enable WiFi manually
            openWifiSettings()
            false
        } else {
            wifiManager.isWifiEnabled = true
            wifiManager.isWifiEnabled
        }
    }

    /**
     * Register network callback to monitor ESP32 connection
     */
    fun registerNetworkCallback(
        onAvailable: (Network) -> Unit,
        onLost: () -> Unit
    ): ConnectivityManager.NetworkCallback {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "📡 Network available: $network")
                onAvailable(network)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "📡 Network lost: $network")
                onLost()
            }
        }

        connectivityManager.registerNetworkCallback(request, callback)
        return callback
    }

    /**
     * Unregister network callback
     */
    fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error unregistering callback: ${e.message}")
        }
    }
}

sealed class ConnectionResult {
    object Success : ConnectionResult()
    data class NeedsUserAction(val message: String) : ConnectionResult()
    data class Error(val message: String) : ConnectionResult()
}

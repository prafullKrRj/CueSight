package com.cuegight.cuesight.core.network

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Manages WiFi connection to ESP32_CAM
 * Handles both Android 10+ (WifiNetworkSuggestion) and Android 9- (direct connection)
 */
class ConnectionManager(private val context: Context) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val ESP32_SSID = "ESP32_CAM_P"
        private const val ESP32_PASSWORD = "12345678"
        private const val ESP32_IP = "192.168.4.1" // Default ESP32 AP IP
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
     * Uses multiple methods to detect connection
     */
    fun isConnectedToESP32(): Boolean {
        // Method 1: Try to get SSID directly
        val wifiInfo = wifiManager.connectionInfo
        val rawSsid = wifiInfo.ssid
        val ssid = rawSsid?.removeSurrounding("\"")?.trim() ?: ""

        Log.d(TAG, "WiFi Status Check:")
        Log.d(TAG, "  Raw SSID: $rawSsid")
        Log.d(TAG, "  Cleaned SSID: '$ssid'")
        Log.d(TAG, "  Expected SSID: '$ESP32_SSID'")

        // Check if SSID matches (case-insensitive)
        if (ssid.isNotEmpty() && !ssid.equals("<unknown ssid>", ignoreCase = true)) {
            val isConnected = ssid.equals(ESP32_SSID, ignoreCase = true)
            Log.d(TAG, "  Connected via SSID match: $isConnected")
            return isConnected
        }

        // Method 2: Check if we have location permission (required for SSID on Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasLocationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasLocationPermission) {
                Log.w(TAG, "  ⚠️ Location permission not granted, SSID will show as <unknown ssid>")
                // Try alternative method using network capabilities
                return isConnectedToESP32ViaNetworkInfo()
            }
        }

        // Method 3: Check connection via network capabilities
        return isConnectedToESP32ViaNetworkInfo()
    }

    /**
     * Alternative method to check ESP32 connection using network info
     * Checks if connected to a WiFi network without internet (typical for ESP32 AP mode)
     */
    private fun isConnectedToESP32ViaNetworkInfo(): Boolean {
        try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

            // Check if connected to WiFi
            val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

            // ESP32 AP typically doesn't have internet
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                             networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            val isProbablyESP32 = isWifi && !hasInternet

            Log.d(TAG, "  Network Capabilities Check:")
            Log.d(TAG, "    Is WiFi: $isWifi")
            Log.d(TAG, "    Has Internet: $hasInternet")
            Log.d(TAG, "    Probably ESP32: $isProbablyESP32")

            return isProbablyESP32
        } catch (e: Exception) {
            Log.e(TAG, "  Error checking network capabilities: ${e.message}")
            return false
        }
    }

    /**
     * Get current WiFi SSID
     * Returns null if unable to determine
     */
    fun getCurrentSSID(): String? {
        val wifiInfo = wifiManager.connectionInfo
        val rawSsid = wifiInfo.ssid?.removeSurrounding("\"")?.trim()

        // Don't return "<unknown ssid>" as a valid SSID
        return if (rawSsid.isNullOrEmpty() || rawSsid.equals("<unknown ssid>", ignoreCase = true)) {
            // Try to get info from network capabilities
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

            if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (!hasInternet) {
                    "WiFi network (permission required to see name)"
                } else {
                    "WiFi network"
                }
            } else {
                null
            }
        } else {
            rawSsid
        }
    }

    /**
     * Check if location permission is granted (needed for SSID access on Android 10+)
     */
    fun hasLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older versions
        }
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

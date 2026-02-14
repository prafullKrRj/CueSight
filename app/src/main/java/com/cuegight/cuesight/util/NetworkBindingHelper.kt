package com.cuegight.cuesight.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Helper class to bind network operations to a specific WiFi network.
 * This is critical for connecting to IoT devices (like ESP32) that don't provide internet.
 * Android automatically routes traffic through mobile data when WiFi has no internet,
 * which causes ENETUNREACH errors. This helper forces traffic through the WiFi interface.
 */
object NetworkBindingHelper {
    private const val TAG = "NetworkBindingHelper"
    private const val NETWORK_BINDING_TIMEOUT_MS = 10000L

    // Keep a reference to the callback to prevent network unbinding
    private var activeCallback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null

    /**
     * Finds and maintains binding to the WiFi network, even if it doesn't have internet.
     * This binding persists until explicitly released.
     * Returns the Network object if successful, null otherwise.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun bindToWiFiNetwork(context: Context): Network? {
        // If already bound, return existing network
        boundNetwork?.let {
            Log.d(TAG, "Already bound to network: $it")
            return it
        }

        return withTimeoutOrNull(NETWORK_BINDING_TIMEOUT_MS) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkDeferred = CompletableDeferred<Network?>()

            // Build a request specifically for WiFi networks without requiring internet
            // This tells Android to KEEP this network active even without internet
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "WiFi network found: $network")

                    // Bind the entire process to this network
                    val bindResult = connectivityManager.bindProcessToNetwork(network)
                    if (bindResult) {
                        Log.d(TAG, "✅ Successfully bound process to WiFi network")
                        boundNetwork = network
                        networkDeferred.complete(network)
                    } else {
                        Log.e(TAG, "❌ Failed to bind process to WiFi network")
                        networkDeferred.complete(null)
                    }
                }

                override fun onLost(network: Network) {
                    Log.w(TAG, "⚠️ WiFi network lost: $network")
                    if (boundNetwork == network) {
                        boundNetwork = null
                    }
                }

                override fun onUnavailable() {
                    Log.e(TAG, "❌ WiFi network unavailable")
                    if (!networkDeferred.isCompleted) {
                        networkDeferred.complete(null)
                    }
                }
            }

            try {
                // Keep the callback reference to maintain the network binding
                activeCallback = callback

                // Request the network - this keeps it active
                connectivityManager.requestNetwork(request, callback)
                Log.d(TAG, "Requested WiFi network binding...")

                networkDeferred.await()
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting WiFi network: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Unbinds the process from any specific network and releases the network request.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun unbindNetwork(context: Context) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            // Unregister callback if exists
            activeCallback?.let {
                try {
                    connectivityManager.unregisterNetworkCallback(it)
                    Log.d(TAG, "Unregistered network callback")
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering callback: ${e.message}")
                }
                activeCallback = null
            }

            // Unbind process
            connectivityManager.bindProcessToNetwork(null)
            boundNetwork = null
            Log.d(TAG, "Unbound from specific network")
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding network: ${e.message}", e)
        }
    }

    /**
     * Gets the current WiFi network if available.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getCurrentWiFiNetwork(context: Context): Network? {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networks = connectivityManager.allNetworks

            for (network in networks) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    Log.d(TAG, "Found WiFi network: $network")
                    return network
                }
            }
            Log.w(TAG, "No WiFi network found")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WiFi network: ${e.message}", e)
        }
        return null
    }

    /**
     * Checks if currently bound to a WiFi network
     */
    fun isBound(): Boolean = boundNetwork != null
}


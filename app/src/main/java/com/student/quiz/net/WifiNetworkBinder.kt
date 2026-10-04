package com.student.quiz.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Binds outgoing sockets to the Wi-Fi network.
 *
 * In a classroom Wi-Fi with no internet, Android may hand app traffic to mobile
 * data, and connections to the instructor's 192.168.x.x then fail. Requesting
 * the Wi-Fi network explicitly and using its socket factory avoids that.
 * Losing and regaining Wi-Fi is reported so the connection can be rebuilt.
 */
class WifiNetworkBinder(context: Context) {

    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var requested: Network? = null

    /** Called when the Wi-Fi network goes away (socket will stop working). */
    var onLost: (() -> Unit)? = null

    /** Called when Wi-Fi comes back, with the new Network (null on failure). */
    var onAvailable: ((Network?) -> Unit)? = null

    val currentNetwork: Network? get() = requested

    /** Asks the system for a Wi-Fi network and keeps the request alive. */
    fun acquire() {
        if (callback != null) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                requested = network
                onAvailable?.invoke(network)
            }

            override fun onLost(network: Network) {
                if (requested == network) requested = null
                onLost?.invoke()
            }
        }
        callback = cb
        try {
            cm.requestNetwork(request, cb, handler)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot request Wi-Fi network", e)
        }
    }

    /** Releases the Wi-Fi request. Safe to call when nothing was acquired. */
    fun release() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        requested = null
    }

    private companion object {
        const val TAG = "WifiNetworkBinder"
    }
}
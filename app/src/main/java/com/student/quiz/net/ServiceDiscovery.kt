package com.student.quiz.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Finds the instructor's advertisement (docs/protocol.md "Discovery"):
 * service type _quiz._tcp, instance name quiz-<PIN>, TXT record pin=<PIN>.
 * Resolution gives the host address and the port actually bound, which is not
 * necessarily 8080.
 */
class ServiceDiscovery(context: Context) {

    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var active: Search? = null

    /** Calls [onResult] exactly once with a resolved address, or null on timeout. */
    fun findByPin(pin: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS, onResult: (Endpoint?) -> Unit) {
        stop()
        val search = Search(pin, onResult)
        active = search
        handler.postDelayed(search.timeout, timeoutMs)
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, search.listener)
    }

    fun stop() {
        active?.let { search ->
            search.done = true
            runCatching { nsd.stopServiceDiscovery(search.listener) }
        }
        active = null
    }

    data class Endpoint(val host: String, val port: Int, val pin: String)

    private inner class Search(
        val pin: String,
        val onResult: (Endpoint?) -> Unit,
    ) {
        var done = false
        var resolving = false

        val timeout = Runnable { finish(null, "No quiz found for PIN $pin") }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String?, errorCode: Int) {
                finish(null, "Could not start discovery (error $errorCode). Try scanning the QR code.")
            }

            override fun onStopDiscoveryFailed(type: String?, errorCode: Int) = Unit

            override fun onDiscoveryStarted(type: String?) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                if (resolving) return
                if (!nameMatchesPin(service.serviceName, pin)) return
                resolving = true
                resolve(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(type: String?) = Unit
        }

        private fun resolve(service: NsdServiceInfo) {
            @Suppress("DEPRECATION")
            nsd.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                    resolving = false
                    Log.w(TAG, "Could not resolve ${service.serviceName}: $errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val txtPin = info.attributes[ATTR_PIN]
                        ?.toString(Charsets.UTF_8)
                        ?.trim()
                        ?.trim('"')
                    // Double-check the TXT pin: the instance name alone is not enough.
                    if (txtPin != null && txtPin != pin) {
                        Log.w(TAG, "TXT pin $txtPin != requested $pin")
                        resolving = false
                        return
                    }
                    val host = hostAddress(info)
                    if (host == null) {
                        finish(null, "Found the quiz but could not read its address.")
                        return
                    }
                    finish(Endpoint(host, info.port, pin), null)
                }
            })
        }

        private fun hostAddress(info: NsdServiceInfo): String? =
            info.hostAddresses.firstOrNull()?.hostAddress

        private fun finish(endpoint: Endpoint?, error: String?) {
            if (done) return
            done = true
            handler.removeCallbacks(timeout)
            stop()
            if (endpoint == null) Log.w(TAG, "Discovery failed: $error")
            onResult(endpoint)
        }
    }

    companion object {
        private const val TAG = "ServiceDiscovery"
        const val SERVICE_TYPE = "_quiz._tcp"
        const val ATTR_PIN = "pin"
        const val DEFAULT_TIMEOUT_MS = 8_000L

        /** Instance names are quiz-<PIN>; DNS may write them with a trailing dot. */
        fun nameMatchesPin(serviceName: String?, pin: String): Boolean =
            normalize(serviceName) == normalize("quiz-$pin")

        /** Strips a trailing dot and ignores case, so names compare reliably. */
        fun normalize(value: String?): String =
            (value ?: "").trim().removeSuffix(".").lowercase()
    }
}
package moe.kabii.net

import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.util.extensions.stackTraceString
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy

object Proxies {
    private val proxyPorts = Keys.config[Keys.Socks.proxyPorts]
    private val clients: List<OkHttpClient>

    init {
        val proxies = proxyPorts.mapNotNull { port ->
            try {
                Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress("127.0.0.1", port)
                )
            } catch(e: Exception) {
                LOG.error("Error mapping proxy: $port :: ${e.message}")
                LOG.warn(e.stackTraceString)
                null
            }
        }
        clients = proxies.map { proxy ->
            OkHttpClient.Builder()
                .proxy(proxy)
                .build()
        }
            // add "no proxy" option that uses our common client
            .plus(OkHTTP)
    }

    private val cache = mutableMapOf<Int, OkHttpClient>()

    /**
     * Provides a (possibly) proxied client to be used for a request.
     * The key provided should be something unique enough to ensure requests for the same entity are the same key.
     * The key has no relation to the proxy chosen and will vary between restarts.
     */
    fun getClient(key: Int) = cache.getOrPut(key, clients::random)
}
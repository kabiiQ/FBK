package moe.kabii.net

import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.util.extensions.stackTraceString
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.math.min

object ClientRotation {
    private val addrs = Keys.config[Keys.Net.proxies]
    private val port = Keys.config[Keys.Net.port]
    private val clients: List<OkHttpClient>

    init {
        clients = addrs.mapNotNull { addr ->
            try {
                Proxy(Proxy.Type.HTTP, InetSocketAddress(addr, port))
            } catch(e: Exception) {
                LOG.error("Error defining proxy: $addr :: ${e.message}")
                LOG.warn(e.stackTraceString)
                null
            }
        }.map { proxy ->
            OkHttpClient.Builder()
                .proxy(proxy)
                .build()
        }
            .run { listOf(OkHTTP) + this }
    }

    private val cache = mutableMapOf<Int, OkHttpClient>()

    /**
     * Get the quantity of usable HTTP clients
     */
    val count
    get() = clients.count()

    /**
     * Provides a (possibly) proxied client to be used for a request.
     * The key provided should be something unique enough to ensure requests for the same entity are the same key.
     * The key has no relation to the proxy chosen and will vary between restarts.
     */
    fun getClient(key: Int) = cache.getOrPut(key, clients::random)

    /**
     * Provides a client to be used for a request.
     * The first client (index 0) should be the "base" while any additional are "alternate" clients.
     */
    fun getClientNumber(index: Int) = clients[min(index, clients.size - 1)]
}
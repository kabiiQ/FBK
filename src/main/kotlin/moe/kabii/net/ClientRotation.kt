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
    private val scraper = Keys.config[Keys.Net.scraper]

    private val clients: List<OkHttpClient>
    private val scrapeClient: OkHttpClient?

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
        }.run { listOf(OkHTTP) + this }

        scrapeClient = if(scraper.isNotBlank()) {
            try {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(scraper, port))
                OkHttpClient.Builder()
                    .proxy(proxy)
                    .build()
            } catch(e: Exception) {
                LOG.error("Error defining 'scraper' proxy: $scraper :: ${e.message}")
                null
            }
        } else null
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

    /**
     * Gets the client defined as the alternative to use for scraping, if it is defined.
     * Otherwise returns a client from #getClient
     */
    fun getScraperClient() = scrapeClient ?: clients.last()
}
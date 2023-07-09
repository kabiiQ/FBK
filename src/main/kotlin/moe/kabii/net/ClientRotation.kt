package moe.kabii.net

import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.util.extensions.stackTraceString
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

object ClientRotation {
    private val addrs = Keys.config[Keys.Net.ipv4Rotation]
    private val clients: List<OkHttpClient>

    init {
        clients = addrs.mapNotNull { addr ->
            try {
                Factory(InetAddress.getByName(addr))
            } catch(e: Exception) {
                LOG.error("Error binding IPv4: $addr :: ${e.message}")
                LOG.warn(e.stackTraceString)
                null
            }
        }.map { factory ->
            OkHttpClient.Builder()
                .socketFactory(factory)
                .build()
        }
            .plus(OkHTTP)
    }

    private val cache = mutableMapOf<Int, OkHttpClient>()

    /**
     * Provides a (possibly) proxied client to be used for a request.
     * The key provided should be something unique enough to ensure requests for the same entity are the same key.
     * The key has no relation to the proxy chosen and will vary between restarts.
     */
    fun getClient(key: Int) = cache.getOrPut(key, clients::random)

    private class Factory(private val addr: InetAddress) : SocketFactory() {
        private val fact = getDefault()

        override fun createSocket(): Socket {
            val sock = fact.createSocket()
            sock.bind(InetSocketAddress(addr, 0))
            return sock
        }

        override fun createSocket(host: String?, port: Int) = fact.createSocket(host, port, addr, 0)
        override fun createSocket(host: InetAddress?, port: Int) = fact.createSocket(host, port, addr, 0)
        override fun createSocket(host: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int) = fact.createSocket(host, port, addr, localPort)
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int) =  fact.createSocket(host, port, addr, localPort)
    }
}
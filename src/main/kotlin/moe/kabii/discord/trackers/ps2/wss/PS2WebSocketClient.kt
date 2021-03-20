package moe.kabii.discord.trackers.ps2.wss

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import moe.kabii.LOG
import moe.kabii.discord.tasks.DiscordTaskPool
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class PS2WebSocketClient(val channel: SendChannel<WSSEvent>, addr: URI) : WebSocketClient(addr) {
    private val deserializer = EventDeserializer()
    private val context = CoroutineScope(DiscordTaskPool.ps2WSSThread + CoroutineName("PS2-WSS") + SupervisorJob())

    private var suspension: Continuation<Unit>? = null

    suspend fun connectWait() = suspendCoroutine<Unit> { suspension ->
        this.suspension = suspension
        this.connect()
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        LOG.info("PS2 WebSocket connection closed: $code $reason")
        channel.close()
    }

    override fun onError(ex: Exception) {
        if(suspension != null) {
            suspension?.resumeWithException(ex)
            suspension = null
        }
        LOG.info("PS2 WebSocket error: ${ex.message}")
        LOG.debug(ex.stackTraceToString())
    }

    override fun onMessage(message: String?) {
        //LOG.trace("PS2 WebSocket sent: $message")
        message ?: return
        context.launch {
            val event = deserializer.fromPayload(message)
            if(event != null) {
                //LOG.trace("PS2 event decoded: $event")
                channel.send(event)
            }
        }
    }

    override fun onOpen(handshakedata: ServerHandshake?) {
        if(suspension != null) {
            suspension?.resume(Unit)
            suspension = null // remove continuation for any later errors
        }

        LOG.info("PS2 WebSocket connected")
    }
}
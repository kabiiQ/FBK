package moe.kabii.discord.conversation

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Message
import kotlinx.coroutines.*
import moe.kabii.command.params.DiscordParameters
import moe.kabii.util.DurationParser
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.coroutines.resume

// bit hacky, but unchecked casts are guaranteed by enum type.
@Suppress("UNCHECKED_CAST")
class Conversation (val criteria: ResponseCriteria, val discord: GatewayDiscordClient, private val coroutine: CancellableContinuation<*>, val reactionListener: ReactionListener?) {
    fun test(message: String, full: Message? = null) {
        if(message.isBlank() && criteria.type != ResponseType.MESSAGE) return
        if(message.lowercase().trim() == "exit") {
            coroutine as CancellableContinuation<Any?>
            coroutine.resume(null)
            cancel()
            return
        }
        when(criteria.type) {
            ResponseType.STR -> {
                coroutine as CancellableContinuation<String>
                coroutine.resume(message)
                cancel()
            }
            ResponseType.LINE -> {
                message.lineSequence().firstOrNull()?.run {
                    coroutine as CancellableContinuation<String>
                    coroutine.resume(this)
                    cancel()
                }
            }
            ResponseType.LONG -> {
                message.split(" ").firstOrNull()?.toLongOrNull()?.let { response ->
                    val range = (criteria as LongResponseCriteria).range
                    if(range == null || response in range) {
                        coroutine as CancellableContinuation<Long>
                        coroutine.resume(response)
                        cancel()
                    }
                }
            }
            ResponseType.BOOL -> {
                message.split(" ").firstOrNull()?.run {
                    when {
                        startsWith("y", true) -> true
                        equals("true", true) -> true
                        startsWith("n", true) -> false
                        equals("false", true) -> false
                        else -> null
                    }
                }?.also { bool ->
                    coroutine as CancellableContinuation<Boolean>
                    coroutine.resume(bool)
                    cancel()
                }
            }
            ResponseType.PAGE -> {
                criteria as PageResponseCriteria; coroutine as CancellableContinuation<Page?>
                val first = message.split(" ").first()
                first.toIntOrNull()?.let { requestedPage ->
                    val pages = criteria.page.pageCount
                    if (requestedPage == 0) {
                        coroutine.resume(null)
                        cancel()
                    } else if (requestedPage in 1..pages) {
                        coroutine.resume(criteria.page.copy(current = requestedPage-1))
                        cancel()
                    }
                } ?: first.let { requestedPageDirection ->
                    var newPage = criteria.page
                    when(Direction of requestedPageDirection) {
                        Direction.LEFT -> coroutine.resume(--newPage)
                        Direction.RIGHT -> coroutine.resume(++newPage)
                        Direction.EXIT -> {
                            coroutine.resume(null)
                        }
                        else -> null
                    }
                }?.also {
                    cancel()
                }
            }
            ResponseType.DOUBLE -> {
                message.split(" ").firstOrNull()?.toDoubleOrNull()?.let { double ->
                    criteria as DoubleResponseCriteria
                    if(criteria.range == null || double in criteria.range) {
                        coroutine as CancellableContinuation<Double>
                        coroutine.resume(double)
                        cancel()
                    }
                }
            }
            ResponseType.DURATION -> {
                val duration = DurationParser.tryParse(message)
                if(duration != null) {
                    coroutine as CancellableContinuation<Duration>
                    coroutine.resume(duration)
                    cancel()
                }
            }
            ResponseType.MESSAGE -> {
                if(full != null) {
                    coroutine as CancellableContinuation<Message>
                    coroutine.resume(full)
                    cancel()
                }
            }
        }
    }

    fun cancel() {
        allConversations.remove(this)
        try {
            (coroutine as CancellableContinuation<Any?>).resume(null)
        } catch (e: Exception) {} // already resumed ? should not happen but this may be a race
        reactionListener?.cancel()
    }

    companion object {
        private val allConversations = mutableListOf<Conversation>()

        private val timeoutThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        private val timeouts = CoroutineScope(timeoutThread + SupervisorJob())

        val conversations
            get() = allConversations.toList()

        fun register(criteria: ResponseCriteria, discord: GatewayDiscordClient, callback: CancellableContinuation<*>, reactionListener: ReactionListener? = null, timeout: Long? = 40000): Conversation {
            // only one conversation per channel per user - best option unless we can clearly define the behavior with multiple conversations at once
            conversations.filter { conversation ->
                conversation.criteria.channel == criteria.channel && conversation.criteria.user == criteria.user
            }.forEach(Conversation::cancel)
            val new = Conversation(criteria, discord, callback, reactionListener)
            allConversations.add(new)

            if (timeout != null) {
                timeouts.launch {
                    delay(timeout)
                    if (allConversations.contains(new)) {
                        new.cancel()
                    }
                }
            }
            return new
        }

        fun shutdown(): Int {
            val ending = allConversations.size
            allConversations.forEach(Conversation::cancel)
            return ending
        }
    }
}

internal data class Criteria(val user: Long, val channel: Long) {
    companion object {
        infix fun defaultFor(param: DiscordParameters) = Criteria(param.author.id.asLong(), param.chan.id.asLong())
    }
}

open class ResponseCriteria(val user: Long, val channel: Long, val type: ResponseType, val message: Long? = null)

internal class DoubleResponseCriteria(user: Long, channel: Long, val range: ClosedRange<Double>? = null) : ResponseCriteria(user, channel, ResponseType.DOUBLE)

internal class LongResponseCriteria(user: Long, channel: Long, val range: LongRange?, message: Long?) : ResponseCriteria(user, channel, ResponseType.LONG, message)

internal class BoolResponseCriteria(user: Long, channel: Long, message: Long?) : ResponseCriteria(user, channel, ResponseType.BOOL, message)

internal class PageResponseCriteria(user: Long, channel: Long, message: Long, val page: Page) : ResponseCriteria(user, channel, ResponseType.PAGE, message)

enum class ResponseType { STR, LINE, BOOL, LONG, PAGE, DOUBLE, DURATION, MESSAGE }

enum class Direction(val unicode: String, val identity: String) {
    LEFT("\u25C0", "back"),
    RIGHT("\u27A1", "next"),
    EXIT("\u274C", "exit");

    companion object {
        val reactions = values().map { ReactionInfo(it.unicode, it.identity) }
        infix fun of(name: String): Direction? {
            return values().find { it.identity == name }
        }
    }
}
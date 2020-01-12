package moe.kabii.discord.event

import discord4j.core.event.domain.Event
import kotlinx.coroutines.reactor.mono
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

abstract class EventHandler <E: Event> (val eventType: KClass<E>) {
    abstract suspend fun handle(event: E)

    fun wrapAndHandle(event: Event) = mono {
        @Suppress("UNCHECKED_CAST")
        val casted = requireNotNull(event as? E) { "Handler ${event::class.jvmName} recieved an incorrect event :: $event" }
        handle(casted)
    }
}
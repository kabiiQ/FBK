package moe.kabii.discord.event

import discord4j.core.event.domain.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import reactor.core.publisher.Mono
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

abstract class EventListener <E: Event> (val eventType: KClass<E>) {
    abstract suspend fun handle(event: E)

    fun wrapAndHandle(event: Event): Mono<Unit> {
        return mono {
            @Suppress("UNCHECKED_CAST")
            val casted = requireNotNull(event as? E) { "Handler ${event::class.jvmName} recieved an incorrect event :: $event" }
            val scope = CoroutineScope(SupervisorJob())
            withContext(scope.coroutineContext) {
                handle(casted)
            }
        }
    }
}
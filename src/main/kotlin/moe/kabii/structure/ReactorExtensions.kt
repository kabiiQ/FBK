package moe.kabii.structure

import moe.kabii.LOG
import moe.kabii.rusty.Result
import moe.kabii.rusty.Try
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.SynchronousSink

fun <T, R> Flux<T>.mapNotNull(mapper: (T) -> R?): Flux<R> {
    return handle { obj: T, sink: SynchronousSink<R> ->
        mapper(obj)?.run(sink::next)
    }
}

fun <T: Any> Mono<T>.tryBlock(): Result<T, Throwable> = Try {
    block() ?: throw NullPointerException() // this wasn't necessary until running into certain objects that only return null! and I don't want to make Result take nullable values... this is easier for finding any errors, for now.
}.result.apply { ifErr { e ->
    LOG.warn("Exception suppressed in tryBlock: ${e.message}.")
    e.printStackTrace()
}}

fun <T> Mono<T>.filterNot(predicate: (T) -> Boolean): Mono<T> = filter { !predicate(it) }
fun <T> Flux<T>.filterNot(predicate: (T) -> Boolean): Flux<T> = filter { !predicate(it) }

fun Mono<Void>.success(): Mono<Boolean> = thenReturn(true).onErrorReturn(false)
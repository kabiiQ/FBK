package moe.kabii.util

import java.util.concurrent.locks.Lock

fun <R> lock(lock: Lock, block: () -> R): R {
    lock.lock()
    return try {
        block.invoke()
    } finally {
        lock.unlock()
    }
}

fun <R> tryLock(lock: Lock, withLock: () -> R, withoutLock: () -> R): R {
    return if(lock.tryLock()) {
        try {
            withLock.invoke()
        } finally {
            lock.unlock()
        }
    } else withoutLock.invoke()
}

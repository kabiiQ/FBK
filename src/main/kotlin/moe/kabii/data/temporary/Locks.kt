package moe.kabii.data.temporary

import kotlinx.coroutines.sync.Mutex

abstract class LockCache<T> {
    protected val locks = mutableMapOf<T, Mutex>()
    operator fun get(identifier: T) = locks.getOrPut(identifier) { Mutex() }
}

object Locks {

    // StreamChannel cache by database ID
    object StreamChannel : LockCache<Int>()

    // YoutubeVideo cache by YouTube video ID
    object YoutubeVideo : LockCache<String>() {
        fun purgeLocks() = locks.clear()
    }

    // Bluesky feed cache by did
    object Bluesky : LockCache<String>()
}
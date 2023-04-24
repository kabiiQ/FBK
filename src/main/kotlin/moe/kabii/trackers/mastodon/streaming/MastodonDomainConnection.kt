package moe.kabii.trackers.mastodon.streaming

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.concurrent.thread

class MastodonDomainConnection(val domain: String) {
    private val filterLock = Mutex()
    private val trackedAccounts = mutableMapOf<String, Int>()

    private var connection: Thread? = null

    // when a new user is tracked on this domain, this method should be called to update the list of relevant account ids
    suspend fun filterAdd(accountId: String, dbFeed: Int) {
        filterLock.withLock {
            trackedAccounts[accountId] = dbFeed
        }
    }

    private val process = Runnable {
        /*
            TODO maintain connection to domain
            get initial users for domain
            open streaming feed
            filter tweets to maintained list of accounts
            lock filter while processing tweet?
            trackedAccounts.keys.tolist
         */
    }

    fun connect() {
        check(connection == null)
        val thread = Thread(process, "Mastodon-Domain-$domain")
        thread.start()
        connection = thread
    }
}
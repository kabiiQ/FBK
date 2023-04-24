package moe.kabii.trackers.mastodon.streaming

import kotlinx.coroutines.CoroutineScope
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.relational.mastodon.Mastodon
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.newRequestBuilder
import moe.kabii.util.extensions.propagateTransaction
import java.io.IOException
import kotlin.jvm.Throws

object MastodonConnectionManager {

    private val domainConnections = mutableMapOf<Int, MastodonDomainConnection>()
    suspend fun get(domain: String): MastodonDomainConnection? = Mastodon.Domains
        .get(domain)
        .firstOrNull()
        ?.run { domainConnections[id.value] }

    private fun connectDomain(dbDomain: Mastodon.Domain) {
        val newConnection = MastodonDomainConnection(dbDomain.domain)
        newConnection.connect()
        domainConnections[dbDomain.id.value] = newConnection
    }

    suspend fun connectKnownDomains() {
        // TODO
    }

    /**
     * @newDomain The domain to add to tracking. Should do basic sanity check on this input. Will be forced to lowercase
     */
    @Throws(IOException::class)
    suspend fun newDomainTracked(newDomain: String): Mastodon.Domain? {
        // always using lower case domain for consistency and ease of managing duplication
        val domain = newDomain.lowercase()
        // ensure does not already exist
        if(get(domain) != null) return null

        // perform server checks: verify domain by getting mastodon instance info
        val ping = newRequestBuilder()
            .get()
            .url("https://$domain/api/v2/instance")
            .build()

        // http call may also throw ioexception on its own. don't handle, let propagate
        val instance =  OkHTTP.newCall(ping).execute().use { rs ->
            if(rs.isSuccessful) rs.body else {
                LOG.error("Unable to connect to requested Mastodon instance: $domain :: ${rs.code} :: ${rs.message} :: ${rs.body}")
                throw IOException("Domain returned status code: ${rs.code} :: ${rs.message}")
            }
        }
        LOG.info("Successfully connected to new Mastodon instance: $domain :: $instance")

        // record this new domain in the database
        val dbDomain = Mastodon.Domains.insert(domain)

        // create new domain connection to immediately start receiving statues
        connectDomain(dbDomain)
        return dbDomain
    }
}
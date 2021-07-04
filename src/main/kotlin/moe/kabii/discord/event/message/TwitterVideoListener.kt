package moe.kabii.discord.event.message

import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.trackers.twitter.TwitterParser
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.stackTraceString

object TwitterVideoListener : EventListener<MessageCreateEvent>(MessageCreateEvent::class) {
    private val twitterUrl = Regex("https://(?:mobile\\.)?twitter\\.com/.{4,15}/status/(\\d{19,20})")

    override suspend fun handle(event: MessageCreateEvent) {
        val content = event.message.content
        if(content.isBlank()) return

        val author = event.message.author.orNull()
        if(author?.isBot != false) return // no author or bot author

        val config = event.guildId.orNull()?.run { GuildConfigurations.guildConfigurations[asLong()] }
        if(config?.guildSettings?.twitterVideoLinks == false) return // continue if enabled or PM

        // check message for twitter url
        val tweetId = twitterUrl.find(content)?.groups?.get(1) ?: return

        // request tweet info
        val tweet = try {
            TwitterParser.getV1Tweet(tweetId.value) ?: return
        } catch(e: Exception) {
            LOG.warn("Error getting linked Tweet: ${e.message}")
            LOG.debug(e.stackTraceString)
            return
        }

        val variants = tweet.extended?.media?.mapNotNull { media -> media.video }?.firstOrNull()?.variants ?: return
        val videoUrl = variants
            .filter { it.contentType == "video/mp4" }
            .maxByOrNull { it.bitrate!! }
            ?.url ?: return
        val channel = event.message.channel.awaitSingle()
        channel.createMessage { spec ->
            spec.setMessageReference(event.message.id)
            spec.setContent(videoUrl)
        }.awaitSingle()
    }
}
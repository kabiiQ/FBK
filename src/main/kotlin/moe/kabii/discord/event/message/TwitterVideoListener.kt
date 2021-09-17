package moe.kabii.discord.event.message

import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.discord.conversation.ReactionInfo
import moe.kabii.discord.conversation.ReactionListener
import moe.kabii.discord.event.EventListener
import moe.kabii.trackers.twitter.TwitterParser
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.success
import java.time.Duration

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
        val videoUrl = try {
            TwitterParser.getV1Tweet(tweetId.value)?.findAttachedVideo() ?: return
        } catch(e: Exception) {
            LOG.warn("Error getting linked Tweet: ${e.message}")
            LOG.debug(e.stackTraceString)
            return
        }

        val channel = event.message.channel.awaitSingle()
        val video = channel.createMessage { spec ->
            spec.setMessageReference(event.message.id)
            spec.setContent(videoUrl)
        }.awaitSingle()

        val reaction = ReactionInfo(EmojiCharacters.cancel, "cancel")
        ReactionListener(
            MessageInfo.of(video),
            listOf(reaction),
            author.id.asLong(),
            event.client) { _, _, _ ->
                video.delete().subscribe()
                true
            }.create(video, add = true)
        delay(Duration.ofSeconds(30))
        video.removeReactions(ReactionEmoji.unicode(reaction.unicode)).success().awaitSingle()
    }
}
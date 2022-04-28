package moe.kabii.discord.event.guild

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.TempStates
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.success
import reactor.core.publisher.Mono
import java.time.Duration

enum class ReactionAction { ADD, REMOVE }

object ReactionRoleHandler {
    class ReactionAddListener(val instances: DiscordInstances) : EventListener<ReactionAddEvent>(ReactionAddEvent::class) {
        override suspend fun handle(event: ReactionAddEvent) {
            val guildId = event.guildId.orNull() ?: return
            handleReactionRole(instances[event.client].clientId, event.userId, guildId, event.channelId, event.messageId, event.guild, event.message, event.emoji, ReactionAction.ADD)
        }
    }

    class ReactionRemoveListener(val instances: DiscordInstances) : EventListener<ReactionRemoveEvent>(ReactionRemoveEvent::class) {
        override suspend fun handle(event: ReactionRemoveEvent) {
            val guildId = event.guildId.orNull() ?: return
            handleReactionRole(instances[event.client].clientId, event.userId, guildId, event.channelId, event.messageId, event.guild, event.message, event.emoji, ReactionAction.REMOVE)
        }
    }

    suspend fun handleReactionRole(clientId: Int, userId: Snowflake, guildId: Snowflake, channelId: Snowflake, messageId: Snowflake, guild: Mono<Guild>, message: Mono<Message>, emoji: ReactionEmoji, direction: ReactionAction) {
        // check if this is a registered reaction role message
        val config = GuildConfigurations.getOrCreateGuild(clientId, guildId.asLong())
        val reactionRole = config.autoRoles.reactionConfigurations
            .filter { cfg -> cfg.message.messageID == messageId.asLong() }
            .find { cfg -> cfg.reaction.toReactionEmoji() == emoji }
            ?: return

        val info = "Reaction role message #${reactionRole.message.messageID} / emoji ${reactionRole.reaction.name}"
        val member = guild
            .flatMap { g -> g.getMemberById(userId) }.awaitSingle()!!
        if(member.isBot) return

        val botReaction = TempStates.BotReactionRemove(messageId, userId, emoji)

        val reactionRoleId = reactionRole.role.snowflake
        val action = when(direction) {
            ReactionAction.ADD -> member.addRole(reactionRoleId, info)
            ReactionAction.REMOVE -> {
                // ignore REMOVE event if the bot itself removed this reaction (for 'clean' auto roles)
                if(TempStates.emojiRemove.contains(botReaction)) return
                member.removeRole(reactionRoleId, info)
            }
        }

        try {
            action.thenReturn(Unit).awaitSingle()

            // if "clean" reaction-role config is enabled, remove user reaction here
            val clean = config.options.featureChannels[channelId.asLong()]?.cleanReactionRoles
            if(direction == ReactionAction.ADD && clean == true) {
                val discordMessage = message.awaitSingle()
                delay(Duration.ofSeconds(30))
                TempStates.emojiRemove.add(botReaction)
                discordMessage
                    .removeReaction(emoji, userId)
                    .success().awaitSingle()
                delay(Duration.ofSeconds(2))
                TempStates.emojiRemove.remove(botReaction)
            }

        } catch(e: Exception) {
            LOG.debug("Reaction role error: ${e.message}")

        }
    }
}
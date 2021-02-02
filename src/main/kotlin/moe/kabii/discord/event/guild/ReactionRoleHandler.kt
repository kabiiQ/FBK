package moe.kabii.discord.event.guild

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait
import reactor.core.publisher.Mono
import java.time.Duration

enum class ReactionAction { ADD, REMOVE }

object ReactionRoleHandler {
    object ReactionAddListener : EventListener<ReactionAddEvent>(ReactionAddEvent::class) {
        override suspend fun handle(event: ReactionAddEvent) {
            val guildId = event.guildId.orNull() ?: return
            handleReactionRole(event.userId, guildId, event.channelId, event.messageId, event.guild, event.message, event.emoji, ReactionAction.ADD)
        }
    }

    object ReactionRemoveListener : EventListener<ReactionRemoveEvent>(ReactionRemoveEvent::class) {
        override suspend fun handle(event: ReactionRemoveEvent) {
            val guildId = event.guildId.orNull() ?: return
            handleReactionRole(event.userId, guildId, event.channelId, event.messageId, event.guild, event.message, event.emoji, ReactionAction.REMOVE)
        }
    }

    suspend fun handleReactionRole(userId: Snowflake, guildId: Snowflake, channelId: Snowflake, messageId: Snowflake, guild: Mono<Guild>, message: Mono<Message>, emoji: ReactionEmoji, direction: ReactionAction) {
        // check if this is a registered reaction role message
        val config = GuildConfigurations.getOrCreateGuild(guildId.asLong())
        val reactionRole = config.selfRoles.reactionRoles
            .filter { cfg -> cfg.message.messageID == messageId.asLong() }
            .find { cfg -> cfg.reaction.toReactionEmoji() == emoji }
            ?: return

        val info = "Reaction role message #${reactionRole.message.messageID} / emoji ${reactionRole.reaction.name}"
        val member = guild
            .flatMap { g -> g.getMemberById(userId) }.awaitSingle()!!
        if(member.isBot) return

        val reactionRoleId = reactionRole.role.snowflake
        val action = when(direction) {
            ReactionAction.ADD -> member.addRole(reactionRoleId, info)
            ReactionAction.REMOVE -> member.removeRole(reactionRoleId, info)
        }

        try {
            action.thenReturn(Unit).awaitSingle()

            // if "clean" reaction-role config is enabled, remove user reaction here
            val clean = config.options.featureChannels[channelId.asLong()]?.cleanReactionRoles
            if(direction == ReactionAction.ADD && clean == true) {
                message
                    .delayElement(Duration.ofSeconds(30))
                    .flatMap { m -> m.removeReaction(emoji, userId) }
                    .thenReturn(Unit)
                    .tryAwait()
            }

        } catch(e: Exception) {
            LOG.debug("Reaction role error: ${e.message}")

        }
    }
}
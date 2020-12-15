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
import moe.kabii.rusty.Err
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait
import reactor.core.publisher.Mono

object ReactionRoleHandler {
    object ReactionAddListener : EventListener<ReactionAddEvent>(ReactionAddEvent::class) {
        override suspend fun handle(event: ReactionAddEvent) {
            val guildId = event.guildId.orNull() ?: return
            val config = GuildConfigurations.getOrCreateGuild(guildId.asLong())
            val clean = config.options.featureChannels[event.channelId.asLong()]?.cleanReactionRoles

            handleReactionRole(event.messageId, event.guild, event.message, config, event.userId, event.emoji, clean)
        }
    }

    object ReactionRemoveListener : EventListener<ReactionRemoveEvent>(ReactionRemoveEvent::class) {
        override suspend fun handle(event: ReactionRemoveEvent) {
            val guildId = event.guildId.orNull() ?: return
            val config = GuildConfigurations.getOrCreateGuild(guildId.asLong())

            // if this channel is set up with "clean" reaction roles, ignore reaction "remove" events. if this feature is not enabled, they are handled the same as "add" events.
            val clean = config.options.featureChannels[event.channelId.asLong()]?.cleanReactionRoles
            if(clean == true) return

            handleReactionRole(event.messageId, event.guild, event.message, config, event.userId, event.emoji, clean)
        }
    }

    // handle add and removes the same - they are a toggle depending on the user having the role, not
    suspend fun handleReactionRole(messageId: Snowflake, guild: Mono<Guild>, message: Mono<Message>, config: GuildConfiguration, userId: Snowflake, emoji: ReactionEmoji, clean: Boolean?) {
        // check if this is a registered reaction role message
        val reactionRole = config.selfRoles.reactionRoles
            .filter { cfg -> cfg.message.messageID == messageId.asLong() }
            .find { cfg -> cfg.reaction.toReactionEmoji() == emoji }
            ?: return

        val info = "Reaction role message #${reactionRole.message.messageID} / emoji ${reactionRole.reaction.name}"
        val member = guild
            .flatMap { g -> g.getMemberById(userId) }.awaitSingle()!!

        if(member.isBot) return

        val reactionRoleId = reactionRole.role.snowflake
        val hasRole = member.roleIds.contains(reactionRoleId)

        val action = if(hasRole) member.removeRole(reactionRole.role.snowflake, info)
        else member.addRole(reactionRole.role.snowflake, info)

        try {
            action.thenReturn(Unit).awaitSingle()

            // if "clean" reaction-role config is enabled, remove user reaction here
            if(clean == true) {
                message.flatMap { m -> m.removeReaction(emoji, userId) }
                    .thenReturn(Unit)
                    .tryAwait()
            }

        } catch(e: Exception) {
            LOG.debug("Reaction role error: ${e.message}")

        }
    }
}
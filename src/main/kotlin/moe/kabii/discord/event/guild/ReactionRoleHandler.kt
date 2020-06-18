package moe.kabii.discord.event.guild

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.rusty.Err
import moe.kabii.structure.orNull
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryAwait
import reactor.core.publisher.Mono

object ReactionRoleHandler {
    object ReactionAddListener : EventListener<ReactionAddEvent>(ReactionAddEvent::class) {
        override suspend fun handle(event: ReactionAddEvent) = handleReactionRole(event.messageId, event.guildId.orNull(), event.guild, event.userId, event.emoji)
    }

    object ReactionRemoveListener : EventListener<ReactionRemoveEvent>(ReactionRemoveEvent::class) {
        override suspend fun handle(event: ReactionRemoveEvent) = handleReactionRole(event.messageId, event.guildId.orNull(), event.guild, event.userId, event.emoji)
    }

    // handle add and removes the same - they are a toggle depending on the user having the role, not
    suspend fun handleReactionRole(messageId: Snowflake, guildId: Snowflake?, guild: Mono<Guild>, userId: Snowflake, emoji: ReactionEmoji) {
        val guildID = guildId ?: return
        val config = GuildConfigurations.getOrCreateGuild(guildID.asLong())

        // check if this is a registered reaction role message
        val reactionRole = config.selfRoles.reactionRoles
            .filter { cfg -> cfg.message.messageID == messageId.asLong() }
            .find { cfg -> cfg.reaction.toReactionEmoji() == emoji }
            ?: return

        val info = "Reaction role message #${reactionRole.message.messageID} / emoji ${reactionRole.reaction.name}"
        val member = guild
            .flatMap { g -> g.getMemberById(userId) }.awaitSingle()!!

        if(member.isBot) return

        val hasRole = member.roles
            .filter { role -> role.id.asLong() == reactionRole.role }
            .hasElements().awaitSingle()

        val action = if(hasRole) member.removeRole(reactionRole.role.snowflake, info)
        else member.addRole(reactionRole.role.snowflake, info)

        val roleReq = action.thenReturn(Unit).tryAwait()

        if(roleReq is Err) {
            LOG.debug("Reaction role error: ${roleReq.value.message}")
        }
    }
}
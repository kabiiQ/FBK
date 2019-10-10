package moe.kabii.discord.event.guild

import discord4j.core.event.domain.message.ReactionAddEvent
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.structure.snowflake
import moe.kabii.structure.orNull
import moe.kabii.util.EmojiCharacters

object ReactionRoleHandler {
    fun handle(event: ReactionAddEvent) {
        // reaction role listeners
        // can immediately narrow down to unicode reactions in a guild
        val guildID = event.guildId.orNull() ?: return
        val emoji = event.emoji.asUnicodeEmoji().orNull() ?: return
        when(emoji.raw) {
            EmojiCharacters.check, EmojiCharacters.redX -> {}
            else -> return
        }
        val addRole = when(emoji.raw) {
            EmojiCharacters.check -> true
            EmojiCharacters.redX -> false
            else -> return
        }
        // check if this is a registered reaction role message
        val config = GuildConfigurations.getOrCreateGuild(guildID.asLong())
        val reactionRole = config.selfRoles.roleMentionMessages.find { reactRole ->
            reactRole.message.messageID == event.messageId.asLong()
        } ?: return
        event.guild
            .flatMap { guild ->
                guild.getMemberById(event.userId)
            }.flatMap { member ->
                val info = "Reaction role message #${reactionRole.message.messageID}"
                if(addRole) member.addRole(reactionRole.role.snowflake, info)
                else member.removeRole(reactionRole.role.snowflake, info)
            }.block()
    }
}
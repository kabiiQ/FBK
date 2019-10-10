package moe.kabii.discord.invite

import discord4j.core.`object`.entity.Guild
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.tryBlock

object InviteWatcher {
    private val invites: MutableMap<Long, MutableMap<String, Int>> = mutableMapOf()

    fun updateGuild(guild: Guild): Set<String> {
        val newInvites = when(val invites = guild.invites.collectList().tryBlock()) {
            is Ok -> invites.value.map { invite -> invite.code to invite.uses }.toMap().toMutableMap()
            is Err -> {
                println("Missing permissions to view invites for ${guild.id.asString()}")
                return emptySet()
            }
        }

        val guildID = guild.id.asLong()
        val oldInvites = invites[guildID]
        val changedInvites = newInvites.filter { (code, uses) ->
            uses - (oldInvites?.get(code) ?: 0) > 0
        }.keys
        invites[guildID] = newInvites
        return changedInvites
    }
}

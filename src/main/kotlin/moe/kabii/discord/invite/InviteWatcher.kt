package moe.kabii.discord.invite

import discord4j.core.`object`.entity.Guild
import discord4j.rest.http.client.ClientException
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.tryAwait

object InviteWatcher {
    private val invites: MutableMap<Long, MutableMap<String, Int>> = mutableMapOf()

    suspend fun updateGuild(guild: Guild): Set<String> {
        val config = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
        if(config.guildSettings.utilizeInvites) {
            val newInvites = when(val invites = guild.invites.collectList().tryAwait()) {
                is Ok -> invites.value.map { invite -> invite.code to invite.uses }.toMap().toMutableMap()
                is Err -> {
                    val err = invites.value
                    if(err is ClientException && err.status.code() == 403) {
                        LOG.info("Missing permissions to view invites for ${guild.id.asString()}")

                        // don't continue requesting invites if we will get 403d.
                        config.guildSettings.utilizeInvites = false
                        config.save()
                    } else {
                        LOG.warn("Error retrieving invites for ${guild.id.asString()}")
                        LOG.debug(err.stackTraceString)
                    }
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
        } else return emptySet()
    }
}

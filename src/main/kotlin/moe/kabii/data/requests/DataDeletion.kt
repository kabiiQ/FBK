package moe.kabii.data.requests

import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.util.extensions.propagateTransaction

object DataDeletion {

    suspend fun userDataDeletion(userId: Long) {

        // remove all instances of user id from database
        propagateTransaction {
            val dbUser = DiscordObjects.User.find {
                DiscordObjects.Users.userID eq userId
            }.firstOrNull()
            dbUser?.delete()
        }

        GuildConfigurations.guildConfigurations.values.forEach { cfg ->
            val rejoin = cfg.autoRoles.rejoinRoles.remove(userId) != null

            val starboard = cfg.starboardSetup.starred.any { message ->
                if(message.originalAuthorId == userId) {
                    message.originalAuthorId = null
                    true
                } else false
            }

            if(rejoin || starboard) cfg.save()
        }
    }

    suspend fun guildDataDeletion(guildId: Long) {

        // remove all data concerning this guild from the database
        propagateTransaction {
            val dbGuild = DiscordObjects.Guild.find {
                DiscordObjects.Guilds.guildID eq guildId
            }.firstOrNull()
            dbGuild?.delete()
        }

        GuildConfigurations.guildConfigurations
            .filter { (target, _) ->
                target.guildId == guildId
            }
            .forEach { (_, config) ->
                config.removeSelf()
            }
    }
}
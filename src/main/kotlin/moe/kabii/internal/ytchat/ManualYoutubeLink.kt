package moe.kabii.internal.ytchat

import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.hasPermissions
import moe.kabii.command.verify
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.ytchat.LinkedYoutubeAccount
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.userAddress

object ManualYoutubeLink : Command("manualytlink") {
    override val wikiPath: String? = null

    init {
        discord {
            // permission restricted to hope server admins
            member.verify(Permission.MANAGE_ROLES)

            val userArg = args.user("user").awaitSingle()
            val ytArg = args.string("YouTubeID")

            propagateTransaction {
                val dbUser = DiscordObjects.User.getOrInsert(userArg.id.asLong())
                LinkedYoutubeAccount.link(event.client, ytArg, dbUser)
            }

            ireply(Embeds.fbk("Discord user **${userArg.userAddress()}** (${userArg.id.asString()} has been linked to YouTube account ID **$ytArg** (unverified).")).awaitSingle()
        }
    }
}
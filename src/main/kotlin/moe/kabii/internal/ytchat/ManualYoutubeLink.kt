package moe.kabii.internal.ytchat

import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.hasPermissions
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.ytchat.LinkedYoutubeAccount
import moe.kabii.discord.trackers.videos.youtube.YoutubeParser
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.userAddress

object ManualYoutubeLink : Command("adminlink", "manualytlink", "linkytadmin", "manuallink") {
    override val wikiPath: String? = null

    init {
        discord {
            // permission restricted to hope server admins
            val permit = try {
                event.client
                    .getMemberById(862160810918412319L.snowflake, author.id)
                    .awaitSingle()
                    .hasPermissions(Permission.MANAGE_ROLES)

            } catch(ce: ClientException) {
                false
            }

            if(!permit) return@discord

            if(args.size < 2 || !args[0].matches(YoutubeParser.youtubeChannelPattern)) {
                usage("**adminlink** allows specific users to manually link a Discord ID with a YouTube account for membership verification.", "adminlink <yt channel ID> <Discord user @, ID, or name>").awaitSingle()
                return@discord
            }

            val userArg = args.drop(1).joinToString(" ")
            val targetUser = Search.user(this, userArg, guild!!)
            if(targetUser == null) {
                reply(Embeds.error("Unable to find Discord user **$userArg**.")).awaitSingle()
                return@discord
            }

            // adminlink ytlink discord user
            propagateTransaction {
                val dbUser = DiscordObjects.User.getOrInsert(targetUser.id.asLong())
                LinkedYoutubeAccount.link(event.client, args[0], dbUser)
            }

            reply(Embeds.fbk("Discord user **${targetUser.userAddress()}** (${targetUser.id.asString()} has been linked to YouTube account ID **${args[0]}** (unverified).")).awaitSingle()
        }
    }
}
package moe.kabii.command.commands.ytchat

import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.ytchat.LinkedYoutubeAccount
import moe.kabii.net.oauth.discord.DAPI
import moe.kabii.net.oauth.discord.DiscordAuthorization
import moe.kabii.net.oauth.discord.DiscordParser
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString

object YoutubeLink : Command("ytlink", "linkyt", "youtubelink", "linkyoutube", "linktube") {
    override val wikiPath: String? = null

    init {
        discord {
            // initiate discord-yt link process
            if(guild != null) {
                error("**ytlink** is used to verify your YouTube connection on Discord with me (FBK).\nThis command can only be used when messaging me directly!").awaitSingle()
                return@discord
            }

            val oauth = DiscordAuthorization.createNew(chan.id, author.id, DiscordAuthorization.DiscordScopes.CONNECTIONS) { complete ->
                try {
                    val ytConnections = DiscordParser
                        .getUserConnections(complete.accessToken!!)
                        .filter(DAPI.UserConnection::verified)
                        .filter { conn -> conn.type == "youtube" }

                    if(ytConnections.isEmpty()) {
                        error("Your Discord account does not seem to have any YouTube accounts connected to it! You must first link your YouTube account to Discord by going to User Settings -> Connections, then retry linking here.").awaitSingle()
                        return@createNew
                    }

                    propagateTransaction {
                        val dbUser = DiscordObjects.User.getOrInsert(complete.discordUser)
                        ytConnections.forEach { conn ->
                            LinkedYoutubeAccount.link(event.client, conn.accountId, dbUser)
                        }
                    }

                } catch(e: Exception) {
                    LOG.error("Error getting Discord user connections: ${e.message} :: $complete")
                    LOG.info(e.stackTraceString)
                    error("An error occurred in the Discord->FBK linking process.").awaitSingle()
                }
            }
            embed(author) {
                setTitle("YouTube-Discord linking process started!")
                setDescription("**Step 1)** You must have your YouTube account (the one that has membership) linked within Discord by going to User Settings -> Connections. The YouTube connection does not need to be visible on your profile.\n\n**Step 2)** Use the following link to allow me (FBK) to check your Connections on Discord.\n\nAs the discord.com link will confirm, I am **only** asking permission to access your **Connections**. I will remember your YouTube account info, and this information is only used to confirm your YouTube memberships!\n\n[Authorize FBK to view your Connections on Discord](${oauth.authUrl})")
            }.awaitSingle()
        }
    }
}
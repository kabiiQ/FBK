package moe.kabii.command.commands.ytchat

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.data.flat.AvailableServices
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.ytchat.LinkedYoutubeAccount
import moe.kabii.discord.util.Embeds
import moe.kabii.net.oauth.discord.DAPI
import moe.kabii.net.oauth.discord.DiscordAuthorization
import moe.kabii.net.oauth.discord.DiscordParser
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.userAddress

object YoutubeLink : Command("ytlink") {
    override val wikiPath: String? = null

    init {
        chat {
            // initiate discord-yt link process
            if(!AvailableServices.discordOAuth) return@chat
            val oauth = DiscordAuthorization.createNew(interaction.id, author.id, DiscordAuthorization.DiscordScopes.CONNECTIONS) { complete ->
                try {
                    val ytConnections = DiscordParser
                        .getUserConnections(complete.accessToken!!)
                        .filter(DAPI.UserConnection::verified)
                        .filter { conn -> conn.type == "youtube" }

                    if(ytConnections.isEmpty()) {
                        event.editReply()
                            .withEmbeds(Embeds.error("Your Discord account does not seem to have any YouTube accounts connected to it! You must first link your YouTube account to Discord by going to User Settings -> Connections, then retry linking here."))
                            .awaitSingle()
                        return@createNew
                    }

                    propagateTransaction {
                        val dbUser = DiscordObjects.User.getOrInsert(complete.discordUser)
                        ytConnections.forEach { conn ->
                            LinkedYoutubeAccount.link(event.client, conn.accountId, dbUser)
                        }
                    }

                    val plural = if(ytConnections.size > 1) "s " else " "
                    val accounts = plural + ytConnections.joinToString(", ", transform = { conn ->
                        "[${conn.accountId}](${URLUtil.StreamingSites.Youtube.channel(conn.accountId)})"
                    })
                    event.editReply()
                        .withEmbeds(Embeds.fbk("Your Discord account has been associated with the YouTube account$plural: $accounts"))
                        .awaitSingle()

                } catch(e: Exception) {
                    LOG.error("Error getting Discord user connections: ${e.message} :: $complete")
                    LOG.info(e.stackTraceString)
                    event.editReply()
                        .withEmbeds(Embeds.error("An error occurred in the Discord->FBK linking process."))
                        .awaitSingle()
                }
            }
            val embed = Embeds.fbk("**Step 1)** You must have your YouTube account (the one that has membership) linked within Discord by going to User Settings -> Connections. The YouTube connection does not need to be visible on your profile.\n\n**Step 2)** Use the following link to allow me (FBK) to check your Connections on Discord.\n\nAs the discord.com link will confirm, I am **only** asking permission to access your **Connections**. I will remember your YouTube account info, and this information is only used to confirm your YouTube memberships!\n\n[Authorize FBK to view your Connections on Discord](${oauth.authUrl})")
                .withAuthor(EmbedCreateFields.Author.of(author.userAddress(), null, author.avatarUrl))
                .withTitle("YouTube-Discord linking process started!")
            ereply(embed).awaitSingle()
        }
    }
}
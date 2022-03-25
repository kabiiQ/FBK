package moe.kabii.command.commands.ytchat

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.PrivateChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.ytchat.LinkedYoutubeAccount
import moe.kabii.discord.util.Embeds
import moe.kabii.net.oauth.discord.DAPI
import moe.kabii.net.oauth.discord.DiscordAuthorization
import moe.kabii.net.oauth.discord.DiscordParser
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.success
import moe.kabii.util.extensions.userAddress

object YoutubeLink : Command("ytlink", "linkyt", "youtubelink", "linkyoutube", "linktube") {
    override val wikiPath: String? = null

    init {
        discord {
            // initiate discord-yt link process
            val dmChannel = if(guild != null) {
                try {
                    val dm = author.privateChannel.awaitSingle()
                    event.message.addReaction(ReactionEmoji.unicode(EmojiCharacters.mailbox)).success().awaitSingle()
                    dm
                } catch(ce: ClientException) {
                    reply(Embeds.error("I am unable to send you a DM. Please check your privacy settings!")).awaitSingle()
                    return@discord
                }
            } else chan as PrivateChannel

            promptYtLink(event.client, author, dmChannel)
        }
    }

    suspend fun promptYtLink(discord: GatewayDiscordClient, discordUser: User, openDmChannel: PrivateChannel?) {
        val dm = openDmChannel ?: discordUser.privateChannel.awaitSingle()

        val oauth = DiscordAuthorization.createNew(dm.id, discordUser.id, DiscordAuthorization.DiscordScopes.CONNECTIONS) { complete ->
            try {
                val ytConnections = DiscordParser
                    .getUserConnections(complete.accessToken!!)
                    .filter(DAPI.UserConnection::verified)
                    .filter { conn -> conn.type == "youtube" }

                if(ytConnections.isEmpty()) {
                    dm.createMessage(
                        Embeds.error("Your Discord account does not seem to have any YouTube accounts connected to it! You must first link your YouTube account to Discord by going to User Settings -> Connections, then retry linking here.")
                    ).awaitSingle()
                    return@createNew
                }

                propagateTransaction {
                    val dbUser = DiscordObjects.User.getOrInsert(complete.discordUser)
                    ytConnections.forEach { conn ->
                        LinkedYoutubeAccount.link(discord, conn.accountId, dbUser)
                    }
                }

                val plural = if(ytConnections.size > 1) "s " else " "
                val accounts = plural + ytConnections.joinToString(", ", transform = { conn ->
                    "[${conn.accountId}](${URLUtil.StreamingSites.Youtube.channel(conn.accountId)})"
                })
                dm.createMessage(
                    Embeds.fbk("Your Discord account has been associated with the YouTube account$plural: $accounts")
                ).awaitSingle()

            } catch(e: Exception) {
                LOG.error("Error getting Discord user connections: ${e.message} :: $complete")
                LOG.info(e.stackTraceString)
                dm.createMessage(
                    Embeds.error("An error occurred in the Discord->FBK linking process.")
                ).awaitSingle()
            }
        }
        dm.createMessage(
            Embeds.fbk("**Step 1)** You must have your YouTube account (the one that has membership) linked within Discord by going to User Settings -> Connections. The YouTube connection does not need to be visible on your profile.\n\n**Step 2)** Use the following link to allow me (FBK) to check your Connections on Discord.\n\nAs the discord.com link will confirm, I am **only** asking permission to access your **Connections**. I will remember your YouTube account info, and this information is only used to confirm your YouTube memberships!\n\n[Authorize FBK to view your Connections on Discord](${oauth.authUrl})")
                .withAuthor(EmbedCreateFields.Author.of(discordUser.userAddress(), null, discordUser.avatarUrl))
                .withTitle("YouTube-Discord linking process started!")
        ).awaitSingle()
    }
}
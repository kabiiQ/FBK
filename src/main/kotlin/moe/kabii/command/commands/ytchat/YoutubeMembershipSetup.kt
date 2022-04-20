package moe.kabii.command.commands.ytchat

import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.command.verify
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.ytchat.MembershipConfiguration
import moe.kabii.data.relational.streams.youtube.ytchat.MembershipConfigurations
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.tryAwait
import moe.kabii.ytchat.YoutubeMembershipUtil
import org.jetbrains.exposed.sql.transactions.transaction

object YoutubeMembershipSetup : Command("linkyoutubemembers") {

    override val wikiPath: String? = null

    private fun linkChannel(memberConfig: MembershipConfiguration) = "[${memberConfig.streamChannel.lastKnownUsername}](${URLUtil.StreamingSites.Youtube.channel(memberConfig.streamChannel.siteChannelID)})"

    init {
        chat {

            // linkyoutubemembers (yt id else: get)
            member.verify(Permission.MANAGE_GUILD)

            val clientId = client.clientId
            val linkArg = args.optStr("channel")
            if(linkArg == null) {
                // get any active config
                val linkedChannel = transaction {
                    MembershipConfigurations.getForGuild(clientId, target.id)?.run(::linkChannel)
                }

                if(linkedChannel != null) {
                    ireply(Embeds.fbk("**${target.name}** is currently linked to YouTube channel: $linkedChannel."))
                } else {
                    ereply(Embeds.wiki(command, "**/linkyoutubemembership** is used to set up a link that connects YouTube chat members to a Discord role."))
                }.awaitSingle()
                return@chat
            }

            val resetArg = args.optBool("reset")
            if(resetArg == true) {
                // remove existing config
                propagateTransaction {
                    val existing = MembershipConfigurations.getForGuild(clientId, target.id)
                    if(existing != null) {
                        val channelLink = linkChannel(existing)
                        val utils = existing.utils(target)
                        utils.unsync()
                        ireply(Embeds.fbk("Membership link to channel $channelLink has been **removed.**")).awaitSingle()
                    } else {
                        ereply(Embeds.error("There is no active membership configuration for **${target.name}**.")).awaitSingle()
                    }
                }
                return@chat
            }

            // verify youtube channel exists
            val ytChannel = try {
                val yt = YoutubeParser.getChannelFromUnknown(linkArg)
                if(yt == null) {
                    ereply(Embeds.error("Unable to find YouTube channel **$linkArg**.")).awaitSingle()
                    return@chat
                } else yt
            } catch(e: Exception) {
                LOG.info("Error calling YouTube API: ${e.message}")
                LOG.trace(e.stackTraceString)
                ereply(Embeds.error("Error reaching YouTube.")).awaitSingle()
                return@chat
            }

            // create membership role
            val membershipRole = try {
                target.createRole()
                    .withName("YouTube Member")
                    .withColor(Color.CYAN)
                    .withHoist(true)
                    .awaitSingle()
            } catch(ce: ClientException) {
                if(ce.status.code() == 403) {
                    ereply(Embeds.error("I am missing permission to create roles in **${target.name}**.")).awaitSingle()
                    return@chat
                } else throw ce
            }

            // create config in db
            propagateTransaction {
                val existing = MembershipConfigurations.getForGuild(clientId, target.id)
                if(existing != null) {

                    val utils = existing.utils(target)
                    utils.unsync()
                }

                val memberConfig = transaction {
                    MembershipConfiguration.new {
                        this.discordClient = client.clientId
                        this.discordServer = DiscordObjects.Guild.getOrInsert(target.id.asLong())
                        this.streamChannel = TrackedStreams.StreamChannel.getOrInsert(TrackedStreams.DBSite.YOUTUBE, ytChannel.id, ytChannel.name)
                        this.membershipRole = membershipRole.id.asLong()
                    }
                }

                ireply(Embeds.fbk("Memberships for YouTube channel **[${ytChannel.name}](${ytChannel.url})** have been linked to **${target.name}**.\n\nA role has been created for memberships: <@&${membershipRole.id.asString()}>\n\nUsers must link their Discord account with a YouTube connection to FBK using the **ytlink** command to automate membership status.")).tryAwait()

                // sync any known memberships
                YoutubeMembershipUtil
                    .forConfig(event.client, memberConfig)
                    .syncMemberships()
            }
        }
    }
}
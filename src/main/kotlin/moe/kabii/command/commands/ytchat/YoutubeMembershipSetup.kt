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
import moe.kabii.discord.trackers.videos.youtube.YoutubeParser
import moe.kabii.discord.ytchat.YoutubeMembershipUtil
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.tryAwait
import org.jetbrains.exposed.sql.transactions.transaction

object YoutubeMembershipSetup : Command("linkyoutubemembers", "youtubemembershiplink", "linkyoutubemembership", "linkytmembers", "linkytmembership") {

    override val wikiPath: String? = null

    private val resetArg = Regex("(reset|remove|clear|delete|none)", RegexOption.IGNORE_CASE)

    private fun linkChannel(memberConfig: MembershipConfiguration) = "[${memberConfig.streamChannel.lastKnownUsername}](${URLUtil.StreamingSites.Youtube.channel(memberConfig.streamChannel.siteChannelID)})"

    init {
        discord {

            // linkyoutubemembers (yt id else: get)
            member.verify(Permission.MANAGE_GUILD)

            if(args.isEmpty()) {
                // get any active config
                val linkedChannel = transaction {
                    MembershipConfigurations.getForGuild(target.id)?.run(::linkChannel)
                }

                if(linkedChannel != null) {
                    embed("**${target.name}** is currently linked to YouTube channel: $linkedChannel.")
                } else {
                    usage("**linkyoutubemembership** is used to set up a link that connects YouTube chat members to a Discord role.", "linkyoutubemembership <youtube channel ID or \"remove\" to clear>")
                }.awaitSingle()
                return@discord
            }

            if(args[0].matches(resetArg)) {
                // remove existing config
                propagateTransaction {
                    val existing = MembershipConfigurations.getForGuild(target.id)
                    if(existing != null) {
                        val channelLink = linkChannel(existing)
                        val utils = existing.utils(target)
                        utils.unsync()
                        embed("Membership link to channel $channelLink has been **removed.**").awaitSingle()
                    } else {
                        error("There is no active membership configuration for **${target.name}**.").awaitSingle()
                    }
                }
                return@discord
            }

            // verify youtube channel exists
            val ytChannel = try {
                val yt = YoutubeParser.getChannelFromUnknown(args[0])
                if(yt == null) {
                    error("Unable to find YouTube channel **${args[0]}**.").awaitSingle()
                    return@discord
                } else yt
            } catch(e: Exception) {
                LOG.info("Error calling YouTube API: ${e.message}")
                LOG.trace(e.stackTraceString)
                error("Error reaching YouTube.").awaitSingle()
                return@discord
            }

            // create membership role
            val membershipRole = try {
                target.createRole { spec ->
                    spec.setName("YouTube Member")
                    spec.setColor(Color.CYAN)
                    spec.setHoist(true)
                }.awaitSingle()
            } catch(ce: ClientException) {
                if(ce.status.code() == 403) {
                    error("I am missing permission to create roles in **${target.name}**.").awaitSingle()
                    return@discord
                } else throw ce
            }

            // create config in db
            propagateTransaction {
                val existing = MembershipConfigurations.getForGuild(target.id)
                if(existing != null) {

                    val utils = existing.utils(target)
                    utils.unsync()
                }

                val memberConfig = transaction {
                    MembershipConfiguration.new {
                        this.discordServer = DiscordObjects.Guild.getOrInsert(target.id.asLong())
                        this.streamChannel = TrackedStreams.StreamChannel.getOrInsert(TrackedStreams.DBSite.YOUTUBE, ytChannel.id, ytChannel.name)
                        this.membershipRole = membershipRole.id.asLong()
                    }
                }

                embed("Memberships for YouTube channel **[${ytChannel.name}](${ytChannel.url})** have been linked to **${target.name}**.\n\nA role has been created for memberships: <@&${membershipRole.id.asString()}>\n\nUsers must link their Discord account with a YouTube connection to FBK using the **ytlink** command to automate membership status.").tryAwait()

                // sync any known memberships
                YoutubeMembershipUtil
                    .forConfig(event.client, memberConfig)
                    .syncMemberships()
            }
        }
    }
}
package moe.kabii.command.commands.meta

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.snowflake

object MigrationCommand : Command("migration") {
    override val wikiPath: String? = null
    init {
        terminal {
        }
    }
}

object TwitterShutdownNoticeCommand : Command("twittershutdownnotifyall") {
    override val wikiPath: String? = null

    data class ActiveTwitterChannel(val clientId: Int, val guildId: Snowflake?, val channelId: Snowflake)

    init {
        terminal {

            // get all active 'twitter' channels, send one notice to each
            val channels = propagateTransaction {
                TwitterTarget.all()
                    .filter { target ->
                        GuildConfigurations.findFeatures(target)?.twitterTargetChannel == true
                    }
                    .distinctBy { target ->
                        target.discordChannel.channelID
                    }
                    .map { target ->
                        ActiveTwitterChannel(
                            target.discordClient,
                            target.discordChannel.guild?.guildID?.snowflake,
                            target.discordChannel.channelID.snowflake
                        )
                    }
            }

            println("sending final twitter notification to: ${channels.size} channels. continue? y/n")
            if(readLine() == "y") {
                println("beginning final twitter notification process")

                channels.groupBy { channel ->
                    channel.clientId
                }.forEach { (clientId, channels) ->
                    val discord = instances[clientId].client
                    channels.forEach { channel ->

                        try {
                            val discordChannel = discord
                                .getChannelById(channel.channelId)
                                .ofType(GuildMessageChannel::class.java)
                                .awaitSingle()
                            discordChannel.createMessage(
                                Embeds.fbk(
                                    // TODO compose message, send
                                    // twitter api now locked down
                                    // possibility of solution for specific feeds?
                                )
                            )

                        } catch(ce: ClientException) {
                            println("Unable to send message to twitter channel: ${channel.channelId.asLong()} :: ${ce.message}")
                        }
                    }
                }

            } else {
                println("aborting")
            }
        }
    }
}
package moe.kabii.command.commands.configuration.setup

import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

object ChannelFeatures : CommandContainer {
    object ChannelFeatureModule : ConfigurationModule<FeatureChannel>(
        "channel",
        ChannelFeatures,
        BooleanElement("Anime/Manga list tracking", listOf("anime", "media", "manga", "list", "lists", "animetargetchannel"), FeatureChannel::animeTargetChannel),
        BooleanElement("Livestream/video site tracking", listOf("streams", "stream", "twitch", "yt", "youtube", "twitcasting", "twitcast", "streamtargetchannel"), FeatureChannel::streamTargetChannel),
        BooleanElement("Twitter feed tracking", listOf("twitter", "tweets", "twit", "twitr", "tr", "twittertargetchannel"), FeatureChannel::twitterTargetChannel),
        BooleanElement("Event log (See **log** command)", listOf("log", "modlog", "mod", "logs", "userlog", "botlog", "logchannel"), FeatureChannel::logChannel),
        BooleanElement("Music bot commands", listOf("music", "musicbot", "musicchannel"), FeatureChannel::musicChannel),
        BooleanElement("PS2 event tracking", listOf("ps2", "planetside", "planetside2", "ps2channel"), FeatureChannel::ps2Channel),
        BooleanElement("Enable internet search commands", listOf("search", "google", "ud", "wa", "searchcommands"), FeatureChannel::searchCommands),
        BooleanElement("Temporary voice channel creation", listOf("temp", "temporary", "tempchannel", "tempchannels", "tempchannelcreation"), FeatureChannel::tempChannelCreation),
        BooleanElement("Limit track command usage to moderators", listOf("lock", "locked", "limit", "limited"), FeatureChannel::locked),
        BooleanElement("Allow this channel's messages in your starboard (if enabled)", listOf("starboarded", "starboard", "starboardview", "stars", "star", "allowstarboarding"), FeatureChannel::allowStarboarding),
        BooleanElement("Include this channel's messages in any edit/delete log in this server.", listOf("logged", "logmessages"), FeatureChannel::logCurrentChannel)
    )

    object ChannelFeatures : Command("feature", "features", "channelfeatures", "config", "channel") {
        override val wikiPath= "Configuration-Commands#the-config-command-channel-features"

        init {
            discord {
                if(isPM) return@discord
                channelVerify(Permission.MANAGE_CHANNELS)
                val features = features()

                val wasLog = features.logChannel

                val configurator = Configurator(
                    "Feature configuration for #${guildChan.name}",
                    ChannelFeatureModule,
                    features
                )
                if(configurator.run(this)) {
                    // validate default track target, if configured
                    features.validateDefaultTarget()

                    val notifs = mutableListOf<String>()

                    if(!wasLog && features.logChannel) {
                        notifs.add("${chan.mention} is now a log channel. By default this will log nothing in this channel. To change the logs sent to this channel see the **editlog** command.")
                    }

                    config.save()

                    if(notifs.isNotEmpty()) {
                        send(Embeds.fbk(notifs.joinToString("\n\n"))).awaitSingle()
                    }
                }
            }
        }
    }

    object ListFeatureChannels : Command("channels", "featurechannels", "channelconfigs", "channelfeatures") {
        override val wikiPath = "Configuration-Commands#listing-enabled-channel-features-in-the-server"

        init {
            discord {
                // list active feature channels in this guild
                channelVerify(Permission.MANAGE_CHANNELS)
                val features = config.options.featureChannels
                val channels = features.toMap()
                    .filter { (_, features) -> features.anyEnabled() }
                    .mapNotNull { (id, channel) ->
                        when(val result = target.getChannelById(id.snowflake).tryAwait()) {
                            is Ok -> {
                                result.value to channel
                            }
                            is Err -> {
                                (result.value as? ClientException)?.also { err ->
                                    if(err.status.code() == 404) {
                                        features.remove(id)
                                    }
                                }
                                null
                            }
                        }
                    }

                if(channels.isEmpty()) {
                    send(Embeds.fbk("There are no channel-specific features enabled in **${target.name}**.")).awaitSingle()
                    return@discord
                }
                val fields = channels.map { (channel, features) ->
                    val codes = StringBuilder()
                    with(features) {
                        if(logChannel) codes.append("Event Log Channel (log)\n")
                        if(musicChannel) codes.append("Music Commands (music)\n")
                        if(tempChannelCreation) codes.append("Temporary VC Commands (temp)\n")
                    }
                    EmbedCreateFields.Field.of("#${channel.name}", codes.toString().trim(), true)
                }
                send(
                    Embeds.fbk()
                        .withTitle("Channel-specific features in ${target.name}:")
                        .withFields(fields)
                ).awaitSingle()
            }
        }
    }
}
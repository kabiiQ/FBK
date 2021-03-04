package moe.kabii.command.commands.configuration.setup

import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait

object ChannelFeatures : CommandContainer {
    object ChannelFeatureModule : ConfigurationModule<FeatureChannel>(
        "channel",
        BooleanElement("Anime/Manga list tracking", listOf("anime", "media", "manga", "list", "lists"), FeatureChannel::animeChannel),
        BooleanElement("Twitch stream tracking", listOf("twitch"), FeatureChannel::twitchChannel),
        BooleanElement("YouTube channel tracking", listOf("yt", "youtube"), FeatureChannel::youtubeChannel),
        BooleanElement("Twitter feed tracking", listOf("twitter", "tweets", "twit", "twitr", "tr"), FeatureChannel::twitterChannel),
        BooleanElement("Event log (See **log** command)", listOf("log", "modlog", "mod", "logs", "userlog", "botlog"), FeatureChannel::logChannel),
        BooleanElement("Music bot commands", listOf("music", "musicbot"), FeatureChannel::musicChannel),
        BooleanElement("PS2 event tracking", listOf("ps2", "planetside", "planetside2"), FeatureChannel::ps2Channel),
        BooleanElement("Temporary voice channel creation", listOf("temp", "temporary", "tempchannel", "tempchannels"), FeatureChannel::tempChannelCreation),
        BooleanElement("Limit track command usage to moderators", listOf("lock", "locked", "limit", "limited"), FeatureChannel::locked),
        BooleanElement("Allow this channel's messages in your starboard (if enabled)", listOf("starboarded", "starboard", "starboardview", "stars", "star"), FeatureChannel::allowStarboarding)
    )

    object ChannelFeatures : Command("feature", "features", "channelfeatures", "config", "channel") {
        override val wikiPath= "Configuration-Commands#the-config-command-channel-features"

        init {
            discord {
                if(isPM) return@discord
                channelVerify(Permission.MANAGE_CHANNELS)
                val features = features()

                val wasLog = features.logChannel
                val wasAnime = features.animeChannel
                val configurator = Configurator(
                    "Feature configuration for #${guildChan.name}",
                    ChannelFeatureModule,
                    features
                )
                if(configurator.run(this)) {
                    // validate default track target, if configured
                    features.validateDefaultTarget()


                    if(!wasAnime && features.animeChannel) {
                        features.locked = false
                        embed("The **track** command has been unlocked for all users in ${chan.mention} as this is the typical use-case for the anime tracker. To lock the **track** command to channel moderators, you can run **feature lock enable**.").awaitSingle()
                    }

                    config.save()

                    if(!wasLog && features.logChannel) {
                        embed("${chan.mention} is now a log channel. By default this will log nothing in this channel. To change the logs sent to this channel see the **editlog** command.").subscribe()
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
                    embed("There are no channel-specific features enabled in **${target.name}**.").awaitSingle()
                    return@discord
                }
                embed {
                    setTitle("Channel-specific features in ${target.name}:")
                    channels.forEach { (channel, features) ->
                        val codes = StringBuilder()
                        with(features) {
                            if(twitchChannel) codes.append("Twitch Stream Tracker (twitch)\n")
                            if(youtubeChannel) codes.append("YouTube Channel Tracker (youtube)\n")
                            if(animeChannel) codes.append("Anime List Tracker (anime)\n")
                            if(logChannel) codes.append("Event Log Channel (log)\n")
                            if(twitterChannel) codes.append("Twitter Feed Tracker (twitter)\n")
                        }
                        addField("#${channel.name}", codes.toString().trim(), true)
                    }
                }.awaitSingle()
            }
        }
    }
}
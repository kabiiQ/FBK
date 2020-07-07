package moe.kabii.command.commands.configuration.setup

import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.FeatureChannel
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryAwait

object ChannelFeatures : CommandContainer {
    object ChannelFeatureModule : ConfigurationModule<FeatureChannel>(
        "channel",
        BooleanElement("Anime/Manga list tracking", listOf("anime", "media", "manga", "list", "lists"), FeatureChannel::animeChannel),
        BooleanElement("Livestream tracking", listOf("stream", "streams", "twitch"), FeatureChannel::twitchChannel),
        BooleanElement("Event log (See **log** command)", listOf("log", "modlog", "mod", "logs", "userlog", "botlog"), FeatureChannel::logChannel),
        BooleanElement("Music bot commands", listOf("music", "musicbot"), FeatureChannel::musicChannel),
        BooleanElement("Temporary voice channel creation", listOf("temp", "temporary", "tempchannel", "tempchannels"), FeatureChannel::tempChannelCreation)
    )

    object ChannelFeatures : Command("features", "channelfeatures", "config", "channel") {
        override val wikiPath= "Configuration-Commands#the-config-command-channel-features"

        init {
            discord {
                if(isPM) return@discord
                chan as GuildChannel
                channelVerify(Permission.MANAGE_CHANNELS)
                val features = config.getOrCreateFeatures(chan.getId().asLong())

                val wasLog = features.logChannel
                val configurator = Configurator(
                    "Feature configuration for #${chan.name}",
                    ChannelFeatureModule,
                    features
                )
                if(configurator.run(this)) {
                    config.save()
                    if(!wasLog && features.logChannel) {
                        embed("${chan.getMention()} is now a log channel. By default this will log nothing in this channel. To change the logs sent to this channel see the **editlog** command.").subscribe()
                    }
                }
            }
        }
    }

    object ListFeatureChannels : Command("channels", "featurechannels", "channelconfigs") {
        override val wikiPath by lazy { TODO() }

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
                            if(animeChannel) codes.append("Anime List Tracker (anime)\n")
                            if(logChannel) codes.append("Event Log Channel (log)")
                        }
                        addField("#${channel.name}", codes.toString().trim(), true)
                    }
                }.awaitSingle()
            }
        }
    }
}
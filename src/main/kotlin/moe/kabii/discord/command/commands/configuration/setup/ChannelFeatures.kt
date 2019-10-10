package moe.kabii.discord.command.commands.configuration.setup

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Permission
import discord4j.rest.http.client.ClientException
import moe.kabii.data.mongodb.FeatureChannel
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock

object ChannelFeatures : CommandContainer {
    object ChannelFeatureModule : ConfigurationModule<FeatureChannel>(
        "channel",
        BooleanElement("Anime/Manga list tracking", listOf("anime", "media", "manga", "list", "lists"), FeatureChannel::animeChannel),
        BooleanElement("Twitch stream tracking", listOf("twitch", "streams", "stream"), FeatureChannel::twitchChannel),
        BooleanElement("Log channel", listOf("log", "modlog", "mod", "logs", "userlog", "botlog"), FeatureChannel::logChannel),
        BooleanElement("Music bot command channel", listOf("music", "musicbot"), FeatureChannel::musicChannel)
    )

    object ChannelFeatures : Command("features", "channelfeatures", "config", "channel") {
        init {
            discord {
                if(isPM) return@discord
                chan as TextChannel
                member.verify(Permission.MANAGE_CHANNELS)
                val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
                val features = config.getOrCreateFeatures(chan.id.asLong())

                val wasLog = features.logChannel
                val configurator = Configurator(
                    "Feature configuration for #${chan.name}",
                    ChannelFeatureModule,
                    features
                )
                if(configurator.run(this)) {
                    config.save()
                    if(!wasLog && features.logChannel) {
                        embed("${chan.mention} is now a log channel. By default this will log nothing in this channel. To change the logs sent to this channel see the **editlog** command.").subscribe()
                    }
                }
            }
        }
    }

    object ListFeatureChannels : Command("channels", "featurechannels", "channelconfigs") {
        init {
            discord {
                // list active feature channels in this guild
                member.verify(Permission.MANAGE_CHANNELS)
                val features = GuildConfigurations.getOrCreateGuild(target.id.asLong()).options.featureChannels
                val channels = features.toMap()
                    .filter { (_, features) -> features.anyEnabled() }
                    .mapNotNull { (id, channel) ->
                        when(val result = target.getChannelById(id.snowflake).tryBlock()) {
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
                    embed("There are no channel-specific features enabled in **${target.name}**.").block()
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
                }.block()
            }
        }
    }
}
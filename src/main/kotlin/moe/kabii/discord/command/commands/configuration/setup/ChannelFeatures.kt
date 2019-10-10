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
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import moe.kabii.rusty.*

object ChannelFeatures : CommandContainer {
    object ChannelFeatureModule : ConfigurationModule<FeatureChannel>(
        "channel",
        BooleanElement("Anime/Manga list tracking", listOf("anime", "media", "manga", "list", "lists"), FeatureChannel::animeChannel),
        BooleanElement("Twitch stream tracking", listOf("twitch", "streams", "stream"), FeatureChannel::twitchChannel),
        BooleanElement("Log channel", listOf("modlog", "mod", "logs", "userlog", "botlog", "log"), FeatureChannel::logChannel),
        BooleanElement("Music bot command channel", listOf("music", "musicbot"), FeatureChannel::musicChannel)
    )

    object ChannelFeatures : Command("features", "channelfeatures", "config", "channel") {
        init {
            discord {
                val targetChan = when {
                    args.isNotEmpty() -> {
                        val channel = Search.channelByID<TextChannel>(this, args[0])
                        if(channel == null) {
                            error("Unable to configure features for channel **${args[0]}**. Verify this is a server text channel ID.").block()
                            return@discord
                        } else channel
                    }
                    chan is TextChannel -> chan
                    else -> {
                        usage("Execute this command in the target channel or specify the channel.", "editlog #channel").block()
                        return@discord
                    }
                }

                val targetGuild = targetChan.guild.block()
                val member = targetGuild.getMemberById(author.id).block()
                member.verify(Permission.MANAGE_CHANNELS)
                val config = GuildConfigurations.getOrCreateGuild(targetGuild.id.asLong())
                val features = config.getOrCreateFeatures(targetChan.id.asLong())

                val wasLog = features.logChannel
                val configurator = Configurator(
                    "Feature configuration for #${targetChan.name}",
                    ChannelFeatureModule,
                    features
                )
                if(configurator.run(copy(args = args.drop(1)))) {
                    config.save()
                    if(!wasLog && features.logChannel) {
                        embed("${targetChan.mention} is now a log channel. By default this will log nothing in this channel. To change the logs sent to this channel see the **editlog** command.").subscribe()
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
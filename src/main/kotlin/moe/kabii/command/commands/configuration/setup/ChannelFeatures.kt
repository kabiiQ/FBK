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
        BooleanElement("Anime/Manga list tracking", "anime", FeatureChannel::animeTargetChannel),
        BooleanElement("Livestream/video site tracking", "streams", FeatureChannel::streamTargetChannel),
        BooleanElement("Twitter feed tracking", "twitter", FeatureChannel::twitterTargetChannel),
        BooleanElement("Event log (See **log** command)", "logs", FeatureChannel::logChannel),
        BooleanElement("Music bot commands", "music", FeatureChannel::musicChannel),
        BooleanElement("Temporary voice channel creation", "tempvc", FeatureChannel::tempChannelCreation),
        BooleanElement("Limit track command usage to moderators", "restricted", FeatureChannel::locked),
        BooleanElement("Allow this channel's messages in your starboard (if enabled)", "allowstarboarding", FeatureChannel::allowStarboarding),
    )

    object ChannelFeatures : Command("feature") {
        override val wikiPath= "Configuration-Commands#the-feature-command-channel-features"

        init {
            chat {
                if(isPM) return@chat
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
                        event.createFollowup()
                            .withEmbeds(Embeds.fbk(notifs.joinToString("\n\n")))
                            .awaitSingle()
                    }
                }
            }
        }
    }

    object ListFeatureChannels : Command("channels") {
        override val wikiPath = "Configuration-Commands#the-feature-command-channel-features"

        init {
            chat {
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
                    ereply(Embeds.fbk("There are no channel-specific features enabled in **${target.name}**.")).awaitSingle()
                    return@chat
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
                ereply(
                    Embeds.fbk()
                        .withTitle("Channel-specific features in ${target.name}:")
                        .withFields(fields)
                ).awaitSingle()
            }
        }
    }
}
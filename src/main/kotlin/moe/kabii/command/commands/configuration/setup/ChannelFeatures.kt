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
import moe.kabii.util.constants.Opcode
import moe.kabii.util.extensions.opcode
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

object ChannelFeatures : CommandContainer {
    object ChannelFeatureModule : ConfigurationModule<FeatureChannel>(
        "channel",
        ChannelFeatures,
        BooleanElement("Anime/Manga list tracking", "anime", FeatureChannel::animeTargetChannel),
        BooleanElement("Livestream/video site tracking", "streams", FeatureChannel::streamTargetChannel),
        BooleanElement("Social media feed tracking", "posts", FeatureChannel::postsTargetChannel),
        BooleanElement("HoloChats chat relay", "holochats", FeatureChannel::holoChatsTargetChannel),
        BooleanElement("Event log (See **log** command)", "logs", FeatureChannel::logChannel),
        BooleanElement("Music bot commands (if available)", "music", FeatureChannel::musicChannel),
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
                val wasMusic = features.musicChannel

                val configurator = Configurator(
                    "Feature configuration for #${guildChan.name}",
                    ChannelFeatureModule,
                    features
                )
                if(configurator.run(this)) {
                    // validate default track target, if configured
                    features.validateDefaultTarget()

                    config.save()

                    if(!wasMusic && features.musicChannel && !client.properties.musicFeaturesEnabled) {
                        features.musicChannel = false
                        config.save()
                        event.createFollowup()
                            .withEmbeds(Embeds.error("Music features are currently not available."))
                            .withEphemeral(true)
                            .awaitSingle()
                    }

                    if(!wasLog && features.logChannel) {
                        event.createFollowup()
                            .withEmbeds(Embeds.fbk("${chan.mention} is now a log channel. By default this will log nothing in this channel. To change the logs sent to this channel see the **/log config** command."))
                            .withEphemeral(true)
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
                    .filter { (_, features) ->
                        features.anyEnabled()
                                || features.anyDefaultDisabled()
                                || !features.locked
                    }
                    .mapNotNull { (id, channel) ->
                        when(val result = target.getChannelById(id.snowflake).tryAwait()) {
                            is Ok -> {
                                result.value to channel
                            }
                            is Err -> {
                                (result.value as? ClientException)?.also { err ->
                                    if(Opcode.notFound(err.opcode)) {
                                        features.remove(id)
                                    }
                                }
                                null
                            }
                        }
                    }

                if(channels.isEmpty()) {
                    ereply(Embeds.fbk("There are no modifications to channel-specific features in **${target.name}**.")).awaitSingle()
                    return@chat
                }
                val fields = channels.map { (channel, features) ->
                    val codes = StringBuilder()
                    with(features) {
                        if(logChannel) codes.appendLine("**Enabled:** Event Log Channel (log)")
                        if(musicChannel) codes.appendLine("**Enabled:** Music Commands (music)")
                        if(tempChannelCreation) codes.appendLine("**Enabled:** Temporary VC Commands (temp)")
                        if(!streamTargetChannel) codes.appendLine("**Disabled:** livestream tracker")
                        if(!postsTargetChannel) codes.appendLine("**Disabled:** Twitter feed tracker")
                        if(!animeTargetChannel) codes.appendLine("**Disabled:** anime list tracker")
                        if(!locked) codes.appendLine("Tracker usage **UNLOCKED** for all users")
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
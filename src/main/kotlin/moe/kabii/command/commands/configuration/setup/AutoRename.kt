package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.ChannelMark
import moe.kabii.data.mongodb.guilds.MongoStreamChannel
import moe.kabii.data.mongodb.guilds.RenameFeature
import moe.kabii.discord.trackers.StreamingTarget
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok

object AutoRename : Command("rename", "autorename", "autoname", "autochannelname") {
    override val wikiPath: String?
        get() = TODO("Not yet implemented")

    object AutoRenameModule : ConfigurationModule<RenameFeature>(
        "automatic channel rename",
        StringElement(
            "Channel name when no streams are live",
            listOf("notlive", "nolive", "nonelive", "not-live"),
            RenameFeature::notLive,
            prompt = "Enter the name this channel should have when none of its tracked streams are live. Use **reset** to set to the current channel name.",
            default = "no-streams-live"
        ),
        StringElement(
            "Channel name prefix",
            listOf("prefix", "liveprefix", "prefixlive", "prefix-live"),
            RenameFeature::livePrefix,
            prompt = "Enter a prefix that will be included at the beginning of the channel name when streams are live. Use **reset** to remove the prefix.",
            default = ""
        ),
        StringElement(
            "Channel name suffix",
            listOf("suffix", "livesuffix", "suffixlive", "suffix-live"),
            RenameFeature::liveSuffix,
            prompt = "Enter a suffix that will be included at the end of the channel name when streams are live. This is less common than using a prefix. Use **reset** to remove the suffix.",
            default = ""
        )
    )

    init {
        discord {
            if(isPM) return@discord
            channelVerify(Permission.MANAGE_CHANNELS)
            val features = config.getOrCreateFeatures(chan.id.asLong())

            if(!features.twitchChannel) {
                error("**#${guildChan.name}** does not have stream tracking enabled.").awaitSingle()
                return@discord
            }

            val action = args.getOrNull(0)
            when(action?.toLowerCase()) {
                "enable" -> {
                    // enable the auto channel rename feature in this channel
                    if(features.streamSettings.renameChannel?.enabled == true) {
                        error("Automatic channel renaming is already enabled in **#${guildChan.name}**. It can be configured by running the **rename** command.").awaitSingle()
                        return@discord
                    }

                    if(features.streamSettings.renameChannel == null) {
                        // create a new feature configuration for the channel rename feature, use current channel name as "not live" message
                        val new = RenameFeature(guildChan.name)
                        features.streamSettings.renameChannel = new
                    } else {
                        features.streamSettings.renameChannel!!.enabled = true
                    }
                    config.save()
                    embed("**rename** feature enabled. This Discord channel will be renamed when tracked streams are live. See the **rename** command for further configuration.").awaitSingle()
                }
                "disable" -> {
                    // disable/reset the auto renamae feature
                    if(features.streamSettings.renameChannel?.enabled != true) {
                        error("Channel renaming is not enabled in **#${guildChan.name}**.").awaitSingle()
                        return@discord
                    }
                    features.streamSettings.renameChannel!!.enabled = false
                    config.save()
                    embed("The **rename** feature has been disabled.")
                }
                "set" -> {
                    val feature = features.streamSettings.renameChannel
                    if (feature == null) {
                        error("The channel renaming feature is not enabled in **#${guildChan.name}**. If you wish to enable it, you can do so with **rename enable**.").awaitSingle()
                        return@discord
                    }

                    // set the character used in the channel name to represent a specific stream
                    // rename <set> (site) <identifier> <emoji/word>
                    val inputArgs = args.drop(1).toMutableList() // drop guaranteed 'set' arg

                    if (inputArgs.size < 2) {
                        usage("**rename set** is used to set a word or emoji displayed in the channel name when a specific stream goes live.", "autorename set <yt channel ID/twitch channel name> <emoji/word>").awaitSingle()
                        return@discord
                    }

                    val mark = inputArgs.removeLast()
                    val siteTarget = when (val findTarget = TargetArguments.parseFor(this, inputArgs)) {
                        is Ok -> findTarget.value
                        is Err -> {
                            usage("Unable to find that livestream channel: ${findTarget.value}", "rename set <yt channel ID/twitch channel name> <emoji/word>").awaitSingle()
                            return@discord
                        }
                    }

                    if (siteTarget.site !is StreamingTarget) {
                        error("The **rename set** command is only supported for **livestream** sources.").awaitSingle()
                        return@discord
                    }

                    val streamInfo = when (val streamCall = siteTarget.site.getChannel(siteTarget.identifier)) {
                        is Ok -> streamCall.value
                        is Err -> {
                            error("Unable to find the **${siteTarget.site.full}** stream **${siteTarget.identifier}**.").awaitSingle()
                            return@discord
                        }
                    }

                    val dbChannel = MongoStreamChannel.of(streamInfo)
                    if(mark.toLowerCase() == "none") {

                        val removed = feature.marks.removeIf { existing ->
                            existing.channel == dbChannel
                        }
                        if(removed) {
                            embed("The live mark for **${streamInfo.displayName}")
                        }
                    } else {
                        val newMark = ChannelMark(dbChannel, mark)
                        feature.marks.removeIf { existing ->
                            existing.channel == dbChannel
                        }
                        feature.marks.add(newMark)

                        embed("The \"live\" mark for **${streamInfo.displayName}** has been set to **$mark**.\nThis will be displayed in the Discord channel name when this stream is live.\n" +
                                "It is recommended to use an emoji to represent a live stream, but you are able to use any combination of characters you wish.\n" +
                                "Note that it is **impossible** to use uploaded/custom emojis in a channel name.").awaitSingle()
                    }
                    config.save()
                }
                "marks" -> {
                    // rename marks -> list configured channel marks
                    val feature = features.streamSettings.renameChannel
                    if (feature == null) {
                        error("The channel renaming feature is not enabled in **#${guildChan.name}**. If you wish to enable it, you can do so with **rename enable**.").awaitSingle()
                        return@discord
                    }

                    if(feature.marks.isEmpty()) {
                        embed("There are no configured channel marks in **#${guildChan.name}**.")
                    } else {

                        val allMarks = feature.marks.joinToString("\n") { mark ->
                            "${mark.channel.site.targetType.full}/${mark.channel.identifier}: ${mark.mark}"
                        }
                        embed {
                            setTitle("Configured stream markers in **#${guildChan.name}**")
                            setDescription(allMarks)
                        }

                    }.awaitSingle()
                }
                else -> { // other args, including null, are valid for configurator run

                    val feature = features.streamSettings.renameChannel
                    if(feature == null) {
                        error("The channel renaming feature is not enabled in **#${guildChan.name}**. If you wish to enable it, you can do so with **rename enable**.").awaitSingle()
                        return@discord
                    }

                    val configurator = Configurator(
                        "Channel/stream renaming configuration for #${guildChan.name}",
                        AutoRenameModule,
                        feature
                    )

                    if(configurator.run(this)) {
                        config.save()
                    }
                }
            }
        }
    }
}
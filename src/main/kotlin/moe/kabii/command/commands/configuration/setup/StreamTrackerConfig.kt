package moe.kabii.command.commands.configuration.setup;

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.ChannelMark
import moe.kabii.data.mongodb.guilds.MongoStreamChannel
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.discord.trackers.StreamingTarget
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok

object StreamTrackerConfig : Command("streamconfig", "twitchconfig", "streamtracker", "twitchtracker", "configtwitch", "twitchembed", "streamembed", "configstreams", "twitchsettings", "streamsettings") {
    override val wikiPath = "Livestream-Tracker#configuration"

    object StreamTrackerModule : ConfigurationModule<StreamSettings>(
        "livestream tracker",
        BooleanElement(
            "Edit stream notification with a summary or VOD information rather than deleting the message when a stream ends",
            listOf("summary", "summarize", "streamsummary"),
            StreamSettings::summaries
        ),
        BooleanElement(
            "Include the current stream thumbnail",
            listOf("thumbnail", "thumbnails", "image", "picture"),
            StreamSettings::thumbnails
        ),
        BooleanElement(
            "Include viewer counts in summary (twitch)",
            listOf("peak", "peakviews", "peakviewers", "viewers"),
            StreamSettings::viewers
        ),
        BooleanElement("Include stream ending title in summary (twitch)",
            listOf("title", "endtitle"),
            StreamSettings::endTitle
        ),
        BooleanElement("Include stream ending game in summary (twitch)",
            listOf("game", "endgame"),
            StreamSettings::endGame
        ),
        BooleanElement("Rename this channel based on live channels",
            listOf("rename", "renamechannel", "renaming", "renam"),
            StreamSettings::renameChannel
        ),
        StringElement(
            "Channel name when no streams are live",
            listOf("notlive", "nolive", "nonelive", "not-live"),
            StreamSettings::notLive,
            prompt = "Enter the name this channel should have when none of its tracked streams are live. Use **reset** to set to the current channel name.",
            default = "no-streams-live"
        ),
        StringElement(
            "Channel name prefix",
            listOf("prefix", "liveprefix", "prefixlive", "prefix-live"),
            StreamSettings::livePrefix,
            prompt = "Enter a prefix that will be included at the beginning of the channel name when streams are live. Use **reset** to remove the prefix.",
            default = ""
        ),
        StringElement(
            "Channel name suffix",
            listOf("suffix", "livesuffix", "suffixlive", "suffix-live"),
            StreamSettings::liveSuffix,
            prompt = "Enter a suffix that will be included at the end of the channel name when streams are live. This is less common than using a prefix. Use **reset** to remove the suffix.",
            default = ""
        )
    )

    init {
        discord {
            if(isPM) return@discord
            channelVerify(Permission.MANAGE_CHANNELS)
            val features = config.getOrCreateFeatures(guildChan.getId().asLong())

            if(!features.isStreamChannel()) {
                error("**#${guildChan.name}** does not have stream tracking enabled.").awaitSingle()
                return@discord
            }

            val action = args.getOrNull(0)
            when(action?.toLowerCase()) {
                "set" -> {
                    val feature = features.streamSettings
                    if (!feature.renameChannel) {
                        error("The channel renaming feature is not enabled in **#${guildChan.name}**. If you wish to enable it, you can do so with **streamconfig rename enable**.").awaitSingle()
                        return@discord
                    }

                    // set the character used in the channel name to represent a specific stream
                    // rename <set> (site) <identifier> <emoji/word>
                    val inputArgs = args.drop(1).toMutableList() // drop guaranteed 'set' arg

                    if (inputArgs.size < 2) {
                        usage("**streamconfig set** is used to set a word or emoji displayed in the channel name when a specific stream goes live.", "autorename set <yt channel ID/twitch channel name> <emoji/word>").awaitSingle()
                        return@discord
                    }

                    val mark = inputArgs.removeLast()
                    val siteTarget = when (val findTarget = TargetArguments.parseFor(this, inputArgs)) {
                        is Ok -> findTarget.value
                        is Err -> {
                            usage("Unable to find that livestream channel: ${findTarget.value}", "streamconfig set <yt channel ID/twitch channel name> <emoji/word>").awaitSingle()
                            return@discord
                        }
                    }

                    if (siteTarget.site !is StreamingTarget) {
                        error("The **streamconfig set** command is only supported for **livestream** sources.").awaitSingle()
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
                    val feature = features.streamSettings
                    if (!feature.renameChannel) {
                        error("The channel renaming feature is not enabled in **#${guildChan.name}**. If you wish to enable it, you can do so with **streamconfig rename enable**.").awaitSingle()
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


                    val configurator = Configurator(
                        "Livestream tracker settings for #${guildChan.name}",
                        StreamTrackerModule,
                        features.streamSettings
                    )

                    if(configurator.run(this)) {
                        config.save()
                    }

                }
            }
        }
    }
}

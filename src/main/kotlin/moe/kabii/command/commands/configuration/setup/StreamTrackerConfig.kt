package moe.kabii.command.commands.configuration.setup;

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.ChannelMark
import moe.kabii.data.mongodb.guilds.MongoStreamChannel
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.StreamingTarget
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.util.extensions.propagateTransaction

object StreamTrackerConfig : Command("streamcfg", "ytconfig", "youtubecfg", "ytcfg", "streamconfig", "twitchconfig", "streamtracker", "twitchtracker", "configtwitch", "twitchembed", "streamembed", "configstreams", "twitchsettings", "streamsettings") {
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
            "Include viewer counts in summary",
            listOf("viewers", "peakviews", "peakviewers", "peak"),
            StreamSettings::viewers
        ),
        BooleanElement("Include stream ending game in summary (twitch)",
            listOf("game", "endgame"),
            StreamSettings::endGame
        ),
        BooleanElement("Use the `setmention` config in this channel",
            listOf("pingRoles", "pings", "ping", "mentions", "mention", "mentionroles"),
            StreamSettings::mentionRoles
        ),
        BooleanElement("Rename this channel based on live channels",
            listOf("rename", "renamechannel", "renaming", "renam"),
            StreamSettings::renameEnabled
        ),
        BooleanElement("Pin active livestreams in this channel",
            listOf("pin", "pins"),
            StreamSettings::pinActive
        ),
        /*BooleanElement("Schedule an event on Discord for upcoming livestreams",
            listOf("events", "scheduleevents", "event"),
            StreamSettings::discordEvents
        ),*/
        StringElement(
            "Channel name when no streams are live",
            listOf("notlive", "nolive", "nonelive", "not-live"),
            StreamSettings::notLive,
            prompt = "Enter the name this channel should have when none of its tracked streams are live.",
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
            val features = features()

            if(!features.streamTargetChannel) {
                send(Embeds.error("**#${guildChan.name}** does not have stream tracking enabled.")).awaitSingle()
                return@discord
            }

            val action = args.getOrNull(0)
            val modified = when(action?.lowercase()) {
                "set" -> {
                    val feature = features.streamSettings
                    if (!feature.renameEnabled) {
                        send(Embeds.error("The channel renaming feature is not enabled in **#${guildChan.name}**. If you wish to enable it, you can do so with **streamcfg rename enable**.")).awaitSingle()
                        return@discord
                    }

                    // set the character used in the channel name to represent a specific stream
                    // rename <set> (site) <identifier> <emoji/word>
                    val inputArgs = args.drop(1).toMutableList() // drop guaranteed 'set' arg

                    if (inputArgs.size < 2) {
                        usage("**streamcfg set** is used to set a word or emoji displayed in the channel name when a specific stream goes live.", "streamcfg set <yt channel ID/twitch channel name> <emoji/word>").awaitSingle()
                        return@discord
                    }

                    val mark = inputArgs.removeLast()
                    val siteTarget = when (val findTarget = TargetArguments.parseFor(this, inputArgs)) {
                        is Ok -> findTarget.value
                        is Err -> {
                            usage("Unable to find that livestream channel: ${findTarget.value}", "streamcfg set <yt channel ID/twitch channel name> <emoji/word>").awaitSingle()
                            return@discord
                        }
                    }

                    if (siteTarget.site !is StreamingTarget) {
                        send(Embeds.error("The **streamcfg set** command is only supported for **livestream** sources.")).awaitSingle()
                        return@discord
                    }

                    val streamInfo = when (val streamCall = siteTarget.site.getChannel(siteTarget.identifier)) {
                        is Ok -> streamCall.value
                        is Err -> {
                            send(Embeds.error("Unable to find the **${siteTarget.site.full}** stream **${siteTarget.identifier}**.")).awaitSingle()
                            return@discord
                        }
                    }

                    val dbChannel = MongoStreamChannel.of(streamInfo)
                    if(arrayOf("none", "reset", "remove").any(mark.lowercase()::equals)) {

                        feature.marks.removeIf { existing ->
                            existing.channel == dbChannel
                        }
                        send(Embeds.fbk("The live mark for **${streamInfo.displayName}** has been removed.")).awaitSingle()
                    } else {
                        val newMark = ChannelMark(dbChannel, mark)
                        feature.marks.removeIf { existing ->
                            existing.channel == dbChannel
                        }
                        feature.marks.add(newMark)

                        send(Embeds.fbk("The \"live\" mark for **${streamInfo.displayName}** has been set to **$mark**.\nThis will be displayed in the Discord channel name when this stream is live.\n" +
                                "It is recommended to use an emoji to represent a live stream, but you are able to use any combination of characters you wish.\n" +
                                "Note that it is **impossible** to use uploaded/custom emojis in a channel name.")).awaitSingle()
                    }
                    true
                }
                "marks" -> {
                    // rename marks -> list configured channel marks
                    val feature = features.streamSettings
                    if (!feature.renameEnabled) {
                        send(Embeds.error("The channel renaming feature is not enabled in **#${guildChan.name}**. If you wish to enable it, you can do so with **streamcfg rename enable**.")).awaitSingle()
                        return@discord
                    }

                    if(feature.marks.isEmpty()) {
                        send(Embeds.fbk("There are no configured channel marks in **#${guildChan.name}**."))
                    } else {
                        val allMarks = feature.marks.joinToString("\n") { mark ->
                            "${mark.channel.site.targetType.full}/${mark.channel.identifier}: ${mark.mark}"
                        }
                        send(
                            Embeds.fbk(allMarks).withTitle("Configured stream markers in **#${guildChan.name}**")
                        )
                    }.awaitSingle()
                    false
                }
                else -> { // other args, including null, are valid for configurator run

                    val wasRename = features.streamSettings.renameEnabled

                    val configurator = Configurator(
                        "Livestream tracker settings for #${guildChan.name}",
                        StreamTrackerModule,
                        features.streamSettings
                    )

                    val modified = configurator.run(this)

                    if(!wasRename && features.streamSettings.renameEnabled) {
                        features.streamSettings.notLive = guildChan.name
                    }
                    modified
                }
            }

            if(modified) {
                config.save()
                propagateTransaction {
                    StreamWatcher.checkAndRenameChannel(chan, null)
                }
            }
        }
    }
}

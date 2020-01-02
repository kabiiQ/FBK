package moe.kabii.discord.command.commands.configuration.setup

import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.VoiceChannel
import discord4j.core.`object`.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.MusicSettings
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryAwait

object MusicConfig : CommandContainer {
    object MusicBot : Command("musicbot", "musicconfig", "musicsetup", "music", "musicsettings") {
        object MusicSettingsModule : ConfigurationModule<MusicSettings>(
            "music bot",
            BooleanElement(
                "Delete old Now Playing bot messages",
                listOf("clean", "deleteold", "delete", "removeold", "clean", "cleanold", "remove"),
                MusicSettings::deleteOldBotCommands
            ),
            BooleanElement(
                "Song owner can force skip song with fskip",
                listOf("ownerskip", "authorskip"),
                MusicSettings::queuerFSkip
            ),
            BooleanElement(
                "Delete old user messages (requires Manage Messages)",
                listOf("cleanuser", "removeuser", "deleteuser"),
                MusicSettings::deleteUserCommnads
            ),
            BooleanElement(
                "Skip command will force skip when permitted",
                listOf("forceskip", "fskip", "force-skip"),
                MusicSettings::alwaysFSkip
            ),
            BooleanElement(
                "Skip song if the requester is no longer in the voice channel",
                listOf("skipIfAbsent"),
                MusicSettings::skipIfAbsent
            ),
            LongElement(
                "User ratio needed for vote skip",
                listOf("ratio", "skipratio"),
                MusicSettings::skipRatio,
                range = 1..100L,
                default = MusicSettings.defaultRatio,
                prompt = "Enter the new value for the vote skip ratio (% of users in channel needed to vote skip). If either the user ratio or the user count votes, the current song will be skipped. For example, type 50 if you want a vote skip to require half the users in the channel."
            ),
            LongElement(
                "User count needed for skip",
                listOf("count", "usercount", "skipcount"),
                MusicSettings::skipUsers,
                range = 1..20L,
                default = MusicSettings.defaultUsers,
                prompt = "Enter the new value for the minimum users to vote skip a song. If either the user ratio or the user count is reached, the current song will be skipped."
            ),
            LongElement(
                "Max tracks in queue for one user (0 = unlimited)",
                listOf("max", "tracks"),
                MusicSettings::maxTracksUser,
                range = 0..Long.MAX_VALUE,
                default = MusicSettings.defaultMaxTracksUser,
                prompt = "Enter the new value for the maximum tracks one user can have in queue at a time. The default value 0 represents unlimited."

            ),
            LongElement(
                "Absolute maximum volume limit",
                listOf("limit"),
                MusicSettings::adminVolumeLimit,
                range = 0..Short.MAX_VALUE.toLong(),
                default = MusicSettings.defaultAdminVolumeLimit,
                prompt = "Enter the new absolute maximum volume that server admins can enter using the volume command. Normal users will still be limited to 5-25 volume and moderators limited within 0-100 volume."
            )
        )

        init {
            discord {
                member.verify(Permission.MANAGE_GUILD)

                val configurator = Configurator(
                    "Music bot settings for ${target.name}",
                    MusicSettingsModule,
                    config.musicBot
                )
                if (configurator.run(this)) {
                    config.save()
                }
            }
        }
    }

    object AutojoinChannel : Command("autojoin", "setautojoin") {
        init {
            discord {
                // ;autojoin chan
                member.verify(Permission.MANAGE_CHANNELS)
                val music = config.musicBot
                // get channel
                if(args.isEmpty()) {
                    val channel = music.autoJoinChannel?.let { id ->
                        event.client.getChannelById(id.snowflake).ofType(VoiceChannel::class.java).tryAwait().orNull()
                    }
                    if(channel == null) {
                        usage("The bot is not currently set to automatically join a voice channel.", "autojoin <channel id or \"set\" for current channel>").awaitSingle()
                        return@discord
                    }
                    embed("The bot is currently set to auto join **${channel.name}**.").awaitSingle()
                    return@discord
                }
                // set channel
                val joinTarget = when(args[0].toLowerCase()) {
                    "none", "reset", "clear", "null", "remove" -> {
                        music.autoJoinChannel = null
                        config.save()
                        embed("The bot has been set to not automatically join any voice channel.").awaitSingle()
                        return@discord
                    }
                    "set", "current", "me", "mine" -> member.voiceState
                        .flatMap(VoiceState::getChannel)
                        .ofType(VoiceChannel::class.java)
                        .tryAwait().orNull()
                    else -> Search.channelByID(this, args[0])
                }
                if(joinTarget == null) {
                    usage("**${args[0]}** does not seem to be a voice channel ID.", "autojoin <channel id or \"set\" for current channel>").awaitSingle()
                    return@discord
                }
                music.autoJoinChannel = joinTarget.id.asLong()
                config.save()
                embed("The bot has been set to automatically join **${joinTarget.name}**.").awaitSingle()
            }
        }
    }
}
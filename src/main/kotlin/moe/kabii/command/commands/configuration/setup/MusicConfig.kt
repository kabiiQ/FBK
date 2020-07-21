package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.data.mongodb.guilds.MusicSettings
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.verify

object MusicConfig : CommandContainer {
    object MusicBot : Command("musicbot", "musicconfig", "musicsetup", "music", "musicsettings") {
        override val wikiPath = "Music-Player#configuration"

        object MusicSettingsModule : ConfigurationModule<MusicSettings>(
            "music bot",
            BooleanElement(
                "Delete old Now Playing bot messages",
                listOf("clean", "deleteold", "delete", "removeold", "clean", "cleanold", "remove"),
                MusicSettings::deleteOldBotMessages
            ),
            BooleanElement(
                """Delete old user "play" commands (requires Manage Messages)""",
                listOf("cleanuser", "removeuser", "deleteuser"),
                MusicSettings::deleteUserCommands
            ),
            BooleanElement(
                "Song owner can force skip song with fskip",
                listOf("ownerperms", "ownerskip", "authorskip"),
                MusicSettings::queuerFSkip
            ),
            BooleanElement(
                "Skip command will force skip when permitted",
                listOf("alwaysfskip", "forceskip", "fskip", "force-skip"),
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
                listOf("max", "tracks", "maxtracks"),
                MusicSettings::maxTracksUser,
                range = 0..Long.MAX_VALUE,
                default = MusicSettings.defaultMaxTracksUser,
                prompt = "Enter the new value for the maximum tracks one user can have in queue at a time. The default value 0 represents unlimited."
            ),
            LongElement(
                "Volume limit",
                listOf("volumeLimit", "maxVolume", "limit"),
                MusicSettings::volumeLimit,
                range = 0..Short.MAX_VALUE.toLong(),
                default = MusicSettings.defaultVolumeLimit,
                prompt = "Enter the new absolute maximum volume that server admins can enter using the volume command. Normal users will still be limited to 5-25 volume and moderators limited within 0-100 volume."
            )
        )

        init {
            discord {
                member.verify(Permission.MANAGE_CHANNELS)

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
}
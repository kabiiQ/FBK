package moe.kabii.command.commands.configuration.setup

import discord4j.rest.util.Permission
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.commands.configuration.setup.base.BooleanElement
import moe.kabii.command.commands.configuration.setup.base.ConfigurationModule
import moe.kabii.command.commands.configuration.setup.base.Configurator
import moe.kabii.command.commands.configuration.setup.base.LongElement
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.MusicSettings

object MusicConfig : CommandContainer {
    object MusicBot : Command("musiccfg") {
        override val wikiPath = "Music-Player#configuration-using-musiccfg"

        object MusicSettingsModule : ConfigurationModule<MusicSettings>(
            "music bot",
            this,
            BooleanElement(
                "Send a message when tracks in queue begin playing",
                "playing",
                MusicSettings::sendNowPlaying
            ),
            BooleanElement(
                "Delete old Now Playing bot messages",
                "deleteold",
                MusicSettings::deleteNowPlaying
            ),
            BooleanElement(
                "Song owner can force skip song with fskip",
                "ownerskip",
                MusicSettings::queuerFSkip
            ),
            BooleanElement(
                "Restrict the usage of audio filters (volume, bass) to users who queued the track",
                "restrictfilters",
                MusicSettings::restrictFilters
            ),
            BooleanElement(
                "Restrict the usage of playback manipulation (ff, seek) to users who queued the track",
                "restrictseek",
                MusicSettings::restrictSeek
            ),
            BooleanElement(
                "Skip command will instantly skip when permitted",
                "forceskip",
                MusicSettings::autoFSkip
            ),
            BooleanElement(
                "Skip song if the requester is no longer in the voice channel",
                "skipIfAbsent",
                MusicSettings::skipIfAbsent
            ),
            LongElement(
                "User ratio needed for vote skip",
                "skipRatio",
                MusicSettings::skipRatio,
                range = 1..100L,
                prompt = "Enter the new value for the vote skip ratio (% of users in channel needed to vote skip)."
            ),
            LongElement(
                "User count needed for skip",
                "skipCount",
                MusicSettings::skipUsers,
                range = 1..20L,
                prompt = "Enter the new value for the minimum users to vote skip a song."
            ),
            LongElement(
                "Max tracks in queue for one user (0 = unlimited)",
                "maxTracks",
                MusicSettings::maxTracksUser,
                range = 0..Short.MAX_VALUE.toLong(),
                prompt = "Enter the new value for the maximum tracks one user can have in queue at a time. The default value (0) represents no track limit."
            ),
            LongElement("Default playback volume",
                "initialVolume",
                MusicSettings::startingVolume,
                range = 1L..100L,
                prompt = "Enter the new starting volume for tracks."
            ),
            LongElement(
                "Volume limit",
                "volumeLimit",
                MusicSettings::volumeLimit,
                range = 0..Short.MAX_VALUE.toLong(),
                prompt = "Enter the new absolute maximum volume that can be used in the volume command."
            )
        )

        init {
            chat {
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
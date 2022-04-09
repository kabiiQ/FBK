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
        override val wikiPath = "Music-Player#configuration"

        object MusicSettingsModule : ConfigurationModule<MusicSettings>(
            "music bot",
            this,
            BooleanElement(
                "Delete old Now Playing bot messages",
                "deleteold",
                MusicSettings::deleteOldBotMessages
            ),
            BooleanElement(
                "Song owner can force skip song with fskip",
                "ownerskip",
                MusicSettings::queuerFSkip
            ),
            BooleanElement(
                "Restrict the usage of audio filters (volume, bass) to users with fskip permission",
                "restrictfilters",
                MusicSettings::restrictFilters
            ),
            BooleanElement(
                "Restrict the usage of playback manipulation (ff, seek) to users with fskip permission",
                "restrictseek",
                MusicSettings::restrictSeek
            ),
            BooleanElement(
                "Skip command will force skip when permitted",
                "alwaysfskip",
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
                prompt = "Enter the new value for the maximum tracks one user can have in queue at a time. The default value 0 represents unlimited."
            ),
            LongElement(
                "Volume limit",
                "volumeLimit",
                MusicSettings::volumeLimit,
                range = 0..Short.MAX_VALUE.toLong(),
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
package moe.kabii.command.commands.audio

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.hasPermissions
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.util.Embeds

object PlaybackState : AudioCommandContainer {
    object PausePlayback : Command("pause") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                audio.player.isPaused = true
                ireply(Embeds.fbk("Audio playback is now paused. You can resume playback with the **resume** command.")).awaitSingle()
            }
        }
    }

    object ResumePlayback : Command("resume") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                audio.player.isPaused = false
                ireply(Embeds.fbk("Audio playback resumed.")).awaitSingle()
            }
        }
    }

    object PlaybackVolume : Command("volume") {
        override val wikiPath = "Music-Player#audio-manipulationfilters"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    val default = config.musicBot.startingVolume
                    ereply(Embeds.error("There is no track currently playing. New tracks will start at **$default%** volume.")).awaitSingle()
                    return@discord
                }
                val targetVolume = args.optInt("volume")?.toInt()
                if(targetVolume == null) {
                    ireply(Embeds.fbk("The current playback volume is **${audio.player.volume}%**.")).awaitSingle()
                    return@discord
                }
                // if volume specified, attempt to change current volume
                if(!canFSkip(this, track)) {
                    ereply(Embeds.error("You must be the DJ (track requester) or be a channel moderator to adjust the playback volume for this track.")).awaitSingle()
                    return@discord
                }
                val data = track.userData as QueueData

                val maximum = config.musicBot.volumeLimit
                if(targetVolume > maximum) {
                    val tip = if(member.hasPermissions(Permission.MANAGE_CHANNELS)) " This can be overridden with the **musicbot** configure command." else ""
                    ireply(Embeds.error("The volume limit is set at **$maximum**.$tip")).awaitSingle()
                    return@discord
                }
                val distort = if(targetVolume > 100) " Setting the volume over 100% will begin to cause audio distortion. " else ""
                val oldVolume = audio.player.volume
                audio.player.volume = targetVolume
                data.volume = targetVolume
                config.save()
                ireply(Embeds.fbk("Changing the playback volume in **${target.name}**: $oldVolume -> **$targetVolume**.$distort")).awaitSingle()
            }
        }
    }
}
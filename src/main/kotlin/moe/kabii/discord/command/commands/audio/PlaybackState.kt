package moe.kabii.discord.command.commands.audio

import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MusicSettings
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.hasPermissions
import moe.kabii.discord.command.verify
import kotlin.math.absoluteValue

object PlaybackState : AudioCommandContainer {
    object PausePlayback : Command("pause", "pausequeue") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                audio.player.isPaused = true
                embed("Audio playback is now paused. You can resume playback with the **resume** command.").block()
            }
        }
    }

    object ResumePlayback : Command("resume", "unpause", "resumequeue", "unpausequeue") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                audio.player.volume
                audio.player.isPaused = false
                embed("Audio playback resumed.").block()
            }
        }
    }

    object PlaybackVolume : Command("volume", "setvolume") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                if(args.isEmpty()) {
                    usage("The current playback volume is **${audio.player.volume}%**.", "volume <integer>").block()
                    return@discord
                }
                // if this is a stream, this may take a minute
                val targetVolume = args[0].removeSuffix("%").toIntOrNull()
                val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
                val maximum = config.musicBot.adminVolumeLimit
                when {
                    targetVolume == null || targetVolume < 0 -> {
                        error("**${args[0]}** is not a valid volume value.").block()
                        return@discord
                    }
                    (targetVolume - MusicSettings.defaultVolume).absoluteValue <= 10 -> {} // +-10: unlocked
                    targetVolume in 0..100 -> member.verify(Permission.MANAGE_MESSAGES) // 0-100: moderator
                    targetVolume > maximum -> {
                        if(!member.hasPermissions(Permission.MANAGE_GUILD))
                        error("The absolute volume limit is set at $maximum. This can be overriden with the **musicbot** configuration command, however this is not recommended.").block()
                        return@discord
                    }
                    else -> member.verify(Permission.MANAGE_GUILD) // > 100: admin
                }
                val distort = if(targetVolume > 100) " Setting the volume over 100% will begin to cause audio distortion. " else ""
                val oldVolume = audio.player.volume
                audio.player.volume = targetVolume
                config.musicBot.volume = targetVolume
                config.save()
                embed("Changing the playback volume in **${target.name}**: $oldVolume -> **$targetVolume**.$distort").block()
            }
        }
    }
}
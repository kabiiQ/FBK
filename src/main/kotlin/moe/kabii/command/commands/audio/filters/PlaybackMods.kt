package moe.kabii.command.commands.audio.filters

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.util.Embeds

object PlaybackMods : AudioCommandContainer {

    suspend fun volume(origin: DiscordParameters) = with(origin) {
        channelFeatureVerify(FeatureChannel::musicChannel)
        val args = subArgs(subCommand)
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        val track = audio.player.playingTrack
        if(track == null) {
            val default = config.musicBot.startingVolume
            ereply(Embeds.error(i18n("audio_volume_no_track", default))).awaitSingle()
            return@with
        }
        val targetVolume = args.optInt("percent")?.toInt()
        if(targetVolume == null) {
            ireply(Embeds.fbk(i18n("audio_volume_current", audio.player.volume))).awaitSingle()
            return@with
        }
        // if volume specified, attempt to change current volume
        if(config.musicBot.restrictFilters && !canFSkip(this, track)) {
            ereply(Embeds.error(i18n("audio_dj_only"))).awaitSingle()
            return@with
        }
        val data = track.userData as QueueData

        val maximum = config.musicBot.volumeLimit
        if(targetVolume > maximum) {
            val tip = if(member.hasPermissions(Permission.MANAGE_CHANNELS)) " ${i18n("audio_volume_override_tip")}" else ""
            ireply(Embeds.error(i18n("audio_volume_maximum", "maximum" to maximum, "tip" to tip))).awaitSingle()
            return@with
        }
        val distort = if(targetVolume > 100) " ${i18n("audio_volume_warning")} " else ""
        val oldVolume = audio.player.volume
        audio.player.volume = targetVolume
        data.volume = targetVolume
        config.save()
        ireply(Embeds.fbk(i18n("audio_volume_set", "server" to target.name, "old" to oldVolume, "new" to targetVolume, "warning" to distort))).awaitSingle()
    }

    suspend fun speed(origin: DiscordParameters) = with(origin) {
        val args = subArgs(subCommand)
        validateAndAlterFilters(this) {
            // /music speed <speed: 10-300>
            val speed = args.int("percent").toDouble() / 100
            addExclusiveFilter(FilterType.Speed(speed))
        }
    }

    suspend fun pitch(origin: DiscordParameters) = with(origin) {
        // /music pitch <pitch: 10-200>
        val args = subArgs(subCommand)
        validateAndAlterFilters(this) {
            val pitch = args.int("percent").toDouble() / 100
            addExclusiveFilter(FilterType.Pitch(pitch))
        }
    }

    suspend fun bass(origin: DiscordParameters) = with(origin) {
        // /music bass (boost: default 100)
        val args = subArgs(subCommand)
        validateAndAlterFilters(this) {
            val boostPct = args.optInt("boost")?.run { toDouble()/100 } ?: 1.0
            addExclusiveFilter(FilterType.Bass(boostPct))
        }
    }

    suspend fun rotation(origin: DiscordParameters) = with(origin) {
        val args = subArgs(subCommand)
        validateAndAlterFilters(this) {
            val speed = args.optDouble("speed")
            val default = .25
            val targetSpeed = when(speed) {
                null -> default
                in .01..5.0 -> speed
                else -> default
            }.toFloat()
            addExclusiveFilter(FilterType.Rotation(targetSpeed))
        }
    }

    // Karaoke in testing: seperate command only sent to dev server
    object KaraokeCommand : Command("karaoke") {
        override val wikiPath: String? = null

        init {
            chat {
                // /music karaoke
                val arg = args.optStr("band")?.lowercase()
                val custom = arg?.toFloatOrNull()
                val (band, name) = if(custom != null) {
                    custom to "custom band: $custom"
                } else {
                    when(arg) {
                        "high", "hi" -> 350f to "high"
                        "low", "lo" -> 100f to "low"
                        else -> 220f to "mid"
                    }
                }
                validateAndAlterFilters(this) {
                    addExclusiveFilter(FilterType.Karaoke(band, name))
                }
            }
        }
    }
}
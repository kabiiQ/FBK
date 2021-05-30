package moe.kabii.command.commands.audio

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.`object`.entity.User
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.rusty.Try
import moe.kabii.util.DurationFormatter
import moe.kabii.util.DurationParser
import moe.kabii.util.extensions.EmbedBlock
import moe.kabii.util.extensions.userAddress
import java.time.Duration
import java.time.temporal.ChronoUnit

object PlaybackSeek : AudioCommandContainer {
    private suspend fun trySeekCurrentTrack(origin: DiscordParameters, track: AudioTrack, target: Duration): Boolean {
        if(!track.isSeekable) {
            origin.error("The current track is not in a seekable format. (For example, streams are not seekable.)").awaitSingle()
            return false
        }
        val millis = Try(target::toMillis).result.orNull()
        if(millis == null || millis !in 0..track.duration) {
            val targetPosition = DurationFormatter(target).colonTime
            val endPosition = DurationFormatter(track.duration).colonTime
            origin.error("The timestamp **$targetPosition** is not valid for the current track. (0:00-$endPosition)").awaitSingle()
            return false
        }
        track.position = millis
        return true
    }

    object SeekPosition : Command("seek", "position") {
        override val wikiPath = "Music-Player#playback-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                if(args.isEmpty()) {
                    usage("**seek** is used to go to a position in a song.", "seek <timestamp>").awaitSingle()
                    return@discord
                }
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There is no track currently playing.").awaitSingle()
                    return@discord
                }
                if(config.musicBot.restrictSeek && !canFSkip(this, track)) {
                    error("You must be the DJ (track requester) or be a channel moderator to alter playback of this track.").awaitSingle()
                    return@discord
                }
                val seekTo = DurationParser.tryParse(noCmd, stopAt = ChronoUnit.HOURS)
                if(seekTo == null) {
                    error("**$noCmd** is not a valid timestamp. Example: **seek 1:12**.").awaitSingle()
                    return@discord
                }
                val targetPosition = DurationFormatter(seekTo).colonTime
                if(trySeekCurrentTrack(this, track, seekTo)) {
                    embed(author, "The position in the currently playing track ${trackString(track)} has been set to **$targetPosition**.").awaitSingle()
                }
            }
        }
    }

    private fun timeSkipMessage(author: User, time: Duration, backwards: Boolean, newPosition: Duration, track: AudioTrack): EmbedBlock {
        val direction = if(backwards) "backwards" else "forwards"
        val positiveTime = DurationFormatter(time).colonTime
        val new = DurationFormatter(newPosition).colonTime

        return {
            setAuthor(author.userAddress(), null, author.avatarUrl)
            setDescription("Moving $direction $positiveTime. in the current track ${trackString(track)} **-> $new**.")
        }
    }

    object PlaybackForward : Command("ff", "fastforward", "forward") {
        override val wikiPath = "Music-Player#playback-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There is no track currently playing.").awaitSingle()
                    return@discord
                }
                if(config.musicBot.restrictSeek && !canFSkip(this, track)) {
                    error("You must be the DJ (track requester) or be a channel moderator to alter playback of this track.").awaitSingle()
                    return@discord
                }
                val seekForwards = if(args.isEmpty()) {
                    Duration.ofSeconds(10)
                } else {
                    val parse = DurationParser.tryParse(noCmd, stopAt = ChronoUnit.HOURS)
                    if(parse == null) {
                        error("**$noCmd** is not a valid length to fast-forward the track.").awaitSingle()
                        return@discord
                    } else parse
                }
                val position = Duration.ofMillis(track.position)
                val seekTo = position.plus(seekForwards)
                if(trySeekCurrentTrack(this, track, seekTo)) {
                    embedBlock(timeSkipMessage(author, seekForwards, false, seekTo, track)).awaitSingle()
                }
            }
        }
    }

    object PlaybackRewind : Command("rewind", "back", "backward", "backwards") {
        override val wikiPath = "Music-Player#playback-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There is no track currently playing.").awaitSingle()
                    return@discord
                }
                if(config.musicBot.restrictSeek && !canFSkip(this, track)) {
                    error("You must be the DJ (track requester) or be a channel moderator to alter playback of this track.").awaitSingle()
                    return@discord
                }
                val seekBackwards = if(args.isEmpty()) {
                    Duration.ofSeconds(10)
                } else {
                    val parse = DurationParser.tryParse(noCmd, stopAt = ChronoUnit.HOURS)
                    if(parse == null) {
                        error("**$noCmd** is not a valid length to rewind the track.").awaitSingle()
                        return@discord
                    } else parse
                }
                val position = Duration.ofMillis(track.position)
                val seekTo = position.minus(seekBackwards)
                if(trySeekCurrentTrack(this, track, seekTo)) {
                    embedBlock(timeSkipMessage(author, seekBackwards, true, seekTo, track)).awaitSingle()
                }
            }
        }
    }
}
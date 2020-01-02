package moe.kabii.discord.command.commands.audio

import discord4j.core.`object`.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.audio.*
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.command.verify
import moe.kabii.util.DurationParser

object QueueTracks : AudioCommandContainer {

    data class TimestampedQuery(val url: String, val timestamp: Long)

    private val timestampRegex = Regex("[&?#]t=([0-9smh]+)")
    fun extractTimestamp(url: String): TimestampedQuery {
        val match = timestampRegex.find(url)
        val timestamp = match?.groups?.get(1)
        val time = if(timestamp != null) {
            DurationParser.tryParse(timestamp.value)
        } else null
        val url = if(timestamp != null) url.replace(match.value, "") else url
        return if(time == null) TimestampedQuery(url, 0) else TimestampedQuery(url, time.toMillis())
    }

    private fun extractQuery(origin: DiscordParameters): TimestampedQuery? {
        val attachment = origin.event.message.attachments.firstOrNull()
        if(attachment != null) {
            // if attached file, try to send this through first
            return TimestampedQuery(attachment.url, timestamp = 0L)
        }
        if(origin.args.isEmpty()) return null
        return extractTimestamp(origin.noCmd)
    }

    object PlaySong : Command("play", "addsong", "queuesong") {
        init {
            discord {
                validateChannel(this) // throws feature exception if this is not a valid "music" channel, caught upstream
                if(!validateVoice(this)) {
                    error("You must be in the bot's voice channel if the bot is in use.").awaitSingle()
                    return@discord
                }
                // add a song to the end of the queue
                val query = extractQuery(this)
                if(query == null) {
                    usage("**play** is used to play an audio track on the bot. You can provide a direct link or let the bot search and play the top Youtube result.", "play <track URL, youtube ID, or youtube search query>").awaitSingle()
                    return@discord
                }
                AudioManager.manager.loadItem(query.url, SingleTrackLoader(this, startingTime = query.timestamp))
            }
        }
    }

    object PlayList : Command("playlist", "listplay") {
        init {
            discord {
                validateChannel(this)
                if(!validateVoice(this)) {
                    error("You must be in the bot's voice channel if the bot is in use.").awaitSingle()
                    return@discord
                }
                if(args.isEmpty()) {
                    usage("**playlist** is used to add a playlist into the bot's music queue.", "playlist <playlist url or youtube ID>").awaitSingle()
                    return@discord
                }
                AudioManager.manager.loadItem(noCmd, PlaylistTrackLoader(this))
            }
        }
    }

    object PlaySongForce : Command("fplay", "forceplay") {
        init {
            discord {
                member.verify(Permission.MANAGE_CHANNELS)
                // immediately start playing track
                val query = extractQuery(this)
                if(query == null) {
                    usage("**fplay** immediately starts playing a track and resumes the current track when finished.", "fplay <track URL, youtube ID, or youtube search query>").awaitSingle()
                    return@discord
                }
                AudioManager.manager.loadItem(query.url, ForcePlayTrackLoader(this, query.timestamp))
            }
        }
    }

    object ReplaySong : Command("replay", "repeat", "requeue") {
        init {
            discord {
                validateChannel(this)
                if(!validateVoice(this)) {
                    error("You must be in the bot's voice channel if the bot is in use.").awaitSingle()
                    return@discord
                }
                // re-queue current song at the end of the queue
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("Nothing is currently playing to be re-queued.").awaitSingle()
                    return@discord
                }
                val newTrack = track.makeClone()
                newTrack.userData = (track.userData as QueueData).apply { votes.clear() }
                val add = audio.tryAdd(track, member)
                if(!add) {
                    val maxTracksUser = config.musicBot.maxTracksUser
                    error {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription("You track was not added to queue because you reached the $maxTracksUser track queue limit set in ${target.name}.")
                    }.awaitSingle()
                    return@discord
                }
                val position = audio.queue.size
                embed("Added the current track **${trackString(track)}** to the end of the queue, position **$position**.").awaitSingle()
            }
        }
    }

    object PlayNext : Command("playnext", "queuenext") {
        init {
            discord {
                validateChannel(this)
                member.verify(Permission.MANAGE_MESSAGES)
                // add a song to the front of the queue
                val query = extractQuery(this)
                if(query == null) {
                    usage("**playnext** adds an audio track to the front of the queue so that it will be played next.", "playnext <track URL, youtube ID, or youtube search query>").awaitSingle()
                    return@discord
                }
                AudioManager.manager.loadItem(query.url, SingleTrackLoader(this, 0, query.timestamp))
            }
        }
    }
}
package moe.kabii.command.commands.audio

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.audio.*

object QueueTracks : AudioCommandContainer {
    object PlaySong : Command("play", "addsong", "queuesong") {
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                validateChannel(this) // throws feature exception if this is not a valid "music" channel, caught upstream
                val voice = AudioStateUtil.checkAndJoinVoice(this)
                if(voice is AudioStateUtil.VoiceValidation.Failure) {
                    error(voice.error).awaitSingle()
                    return@discord
                }
                // add a song to the end of the queue
                val query = ExtractedQuery.from(this)
                if(query == null) {
                    usage("**play** is used to play an audio track on the bot. You can provide a direct link or let the bot search and play the top Youtube result.", "play <track URL, youtube ID, or youtube search query>").awaitSingle()
                    return@discord
                }
                AudioManager.manager.loadItem(query.url, SingleTrackLoader(this, extract = query))
            }
        }
    }

    object PlayList : Command("playlist", "listplay") {
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                validateChannel(this)
                val voice = AudioStateUtil.checkAndJoinVoice(this)
                if(voice is AudioStateUtil.VoiceValidation.Failure) {
                    error(voice.error).awaitSingle()
                    return@discord
                }
                if(args.isEmpty()) {
                    usage("**playlist** is used to add a playlist into the bot's music queue.", "playlist <playlist url or youtube ID>").awaitSingle()
                    return@discord
                }
                val query = ExtractedQuery.default(noCmd)
                AudioManager.manager.loadItem(noCmd, PlaylistTrackLoader(this, extract = query))
            }
        }
    }

    object PlaySongForce : Command("fplay", "forceplay") {
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                channelVerify(Permission.MANAGE_MESSAGES)
                // immediately start playing track
                val query = ExtractedQuery.from(this)
                if(query == null) {
                    usage("**fplay** immediately starts playing a track and resumes the current track when finished.", "fplay <track URL, youtube ID, or youtube search query>").awaitSingle()
                    return@discord
                }
                AudioManager.manager.loadItem(query.url, ForcePlayTrackLoader(this, query))
            }
        }
    }

    object ReplaySong : Command("replay", "repeat", "requeue") {
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                validateChannel(this)
                val voice = AudioStateUtil.checkAndJoinVoice(this)
                if(voice is AudioStateUtil.VoiceValidation.Failure) {
                    error(voice.error).awaitSingle()
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
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                validateChannel(this)
                val voice = AudioStateUtil.checkAndJoinVoice(this)
                if(voice is AudioStateUtil.VoiceValidation.Failure) {
                    error(voice.error).awaitSingle()
                    return@discord
                }
                // add a song to the front of the queue
                val query = ExtractedQuery.from(this)
                if(query == null) {
                    usage("**playnext** adds an audio track to the front of the queue so that it will be played next.", "playnext <track URL, youtube ID, or youtube search query>").awaitSingle()
                    return@discord
                }
                AudioManager.manager.loadItem(query.url, SingleTrackLoader(this, 0, query))
            }
        }
    }
}
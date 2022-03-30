package moe.kabii.command.commands.audio

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.*
import moe.kabii.discord.util.Embeds

object QueueTracks : AudioCommandContainer {
    object PlaySong : Command("play") {
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val voice = AudioStateUtil.checkAndJoinVoice(this)
                if(voice is AudioStateUtil.VoiceValidation.Failure) {
                    ereply(Embeds.error(voice.error)).awaitSingle()
                    return@discord
                }
                val query = ExtractedQuery.from(this)
                if(query == null) {
                    ereply(Embeds.wiki(command, "Provide either text to search (YouTube video ID, YouTube search query, or a direct link to a supported source, or attach a file to be played.")).awaitSingle()
                    return@discord
                }
                // adds a song to the end of queue (front if PlayNext=true)
                val playNext = args.optBool("PlayNext")
                val position = if(playNext == true) 0 else null // default (null) -> false
                AudioManager.manager.loadItem(query.url, SingleTrackLoader(this, position, query))
            }
        }
    }

    object PlayList : Command("playlist") {
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val voice = AudioStateUtil.checkAndJoinVoice(this)
                if(voice is AudioStateUtil.VoiceValidation.Failure) {
                    ereply(Embeds.error(voice.error)).awaitSingle()
                    return@discord
                }
                val playlist = args.string("playlist")
                val query = ExtractedQuery.default(playlist)
                AudioManager.manager.loadItem(playlist, PlaylistTrackLoader(this, extract = query))
            }
        }
    }

    object PlaySongForce : Command("fplay") {
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                channelVerify(Permission.MANAGE_MESSAGES)
                // immediately start playing track
                val query = ExtractedQuery.from(this)
                if(query == null) {
                    ereply(Embeds.wiki(command, "Provide either text to search (YouTube video ID, YouTube search query, or a direct link to a supported source, or attach a file to be played.")).awaitSingle()
                    return@discord
                }
                AudioManager.manager.loadItem(query.url, ForcePlayTrackLoader(this, query))
            }
        }
    }

    object ReplaySong : Command("replay") {
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val voice = AudioStateUtil.checkAndJoinVoice(this)
                if(voice is AudioStateUtil.VoiceValidation.Failure) {
                    ereply(Embeds.error(voice.error)).awaitSingle()
                    return@discord
                }
                // re-queue current song at the end of the queue
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    ereply(Embeds.error("Nothing is currently playing to be re-queued.")).awaitSingle()
                    return@discord
                }
                val newTrack = track.makeClone()
                newTrack.userData = (track.userData as QueueData).apply { votes.clear() }
                val add = audio.tryAdd(track, member)
                if(!add) {
                    val maxTracksUser = config.musicBot.maxTracksUser
                    ereply(Embeds.error("You track was not added to queue because you reached the $maxTracksUser track queue limit set in ${target.name}."))
                    return@discord
                }
                val position = audio.queue.size
                ireply(Embeds.fbk("Added the current track **${trackString(track)}** to the end of the queue, position **$position**.")).awaitSingle()
            }
        }
    }
}
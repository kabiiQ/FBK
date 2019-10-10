package moe.kabii.discord.command.commands.audio

import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.audio.*
import moe.kabii.discord.command.*

object QueueTracks : AudioCommandContainer {
    object PlaySong : Command("play", "addsong", "queuesong") {
        init {
            discord {
                validateChannel(this) // throws feature exception if this is not a valid "music" channel, caught upstream
                if(!validateVoice(this)) {
                    error("You must be in the bot's voice channel if the bot is in use.").block()
                    return@discord
                }
                // add a song to the end of the queue
                if(args.isEmpty()) {
                    usage("**play** is used to play an audio track on the bot. You can provide a direct link or let the bot search and play the top Youtube result.", "play <track URL, youtube ID, or youtube search query>").block()
                    return@discord
                }
                AudioManager.manager.loadItem(noCmd, SingleTrackLoader(this))
            }
        }
    }

    object PlayList : Command("playlist", "listplay") {
        init {
            discord {
                validateChannel(this)
                if(!validateVoice(this)) {
                    error("You must be in the bot's voice channel if the bot is in use.").block()
                    return@discord
                }
                if(args.isEmpty()) {
                    usage("**playlist** is used to add a playlist into the bot's music queue.", "playlist <playlist url or youtube ID>").block()
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
                AudioManager.manager.loadItem(noCmd, ForcePlayTrackLoader(this))
            }
        }
    }

    object ReplaySong : Command("replay", "repeat", "requeue") {
        init {
            discord {
                validateChannel(this)
                if(!validateVoice(this)) {
                    error("You must be in the bot's voice channel if the bot is in use.").block()
                    return@discord
                }
                // re-queue current song at the end of the queue
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("Nothing is currently playing to be re-queued.").block()
                    return@discord
                }
                val newTrack = track.makeClone()
                newTrack.userData = (track.userData as QueueData).apply { votes.set(0) }
                val add = audio.tryAdd(track, member)
                if(!add) {
                    val maxTracksUser = GuildConfigurations.getOrCreateGuild(target.id.asLong()).musicBot.maxTracksUser
                    error {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription("You track was not added to queue because you reached the $maxTracksUser track queue limit set in ${target.name}.")
                    }.block()
                    return@discord
                }
                val position = audio.queue.size
                embed("Added the current track **${trackString(track)}** to the end of the queue, position **$position**.").block()
            }
        }
    }

    object PlayNext : Command("playnext", "queuenext") {
        init {
            discord {
                validateChannel(this)
                member.verify(Permission.MANAGE_MESSAGES)
                // add a song to the front of the queue
                AudioManager.manager.loadItem(noCmd, SingleTrackLoader(this, position = 0))
            }
        }
    }
}
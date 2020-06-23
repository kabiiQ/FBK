package moe.kabii.command.commands.audio

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.sync.withLock
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.command.Command

object QueueSkip : AudioCommandContainer {
    object VoteSkip : Command("skip", "voteskip", "skipsong") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There is no track currently playing.").awaitSingle()
                    return@discord
                }
                if(!canVoteSkip(this, track)) {
                    error("You must be in the bot's voice channel to vote skip.").awaitSingle()
                    return@discord
                }
                val data = track.userData as QueueData
                val votesNeeded = getSkipsNeeded(this)
                val votes = data.voting.withLock {
                    if (data.votes.contains(author.id)) {
                        val votes = data.votes.count()
                        error("You have already voted to skip **${track.info.title}**. ($votes/$votesNeeded votes)").awaitSingle()
                        return@discord
                    }
                    data.votes.add(author.id)
                    data.votes.count()
                }
                if(votes >= votesNeeded) {
                    embed {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription("Skipped **${track.info.title}**.")
                    }.awaitSingle()
                    audio.player.stopTrack()
                } else {
                    embed {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription("Voted to skip **${track.info.title}**. ($votes/$votesNeeded votes)")
                    }.awaitSingle()
                }
            }
        }
    }

    object ForceSkip : Command("fskip", "forceskip", "fskipsong", "skipf") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There are no tracks currently playing.").awaitSingle()
                    return@discord
                }
                if(canFSkip(this, track)) {
                    audio.player.stopTrack()
                    embed {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription("Force-skipped **${track.info.title}**.")
                    }.awaitSingle()
                } else {
                    error {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription("You can not force-skip **${track.info.title}**.")
                    }.awaitSingle()
                }
            }
        }
    }
}
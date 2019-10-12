package moe.kabii.discord.command.commands.audio

import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.command.Command

object QueueSkip : AudioCommandContainer {
    object VoteSkip : Command("skip", "voteskip", "skipsong") {
        init {
            discord {
                validateChannel(this)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    error("There are no tracks currently playing.").block()
                    return@discord
                }
                if(!canVoteSkip(this, track)) {
                    error("You must be in the bot's voice channel to vote skip.").block()
                    return@discord
                }
                val data = track.userData as QueueData
                val votes = data.votes.incrementAndGet()
                val votesNeeded = getSkipsNeeded(this)
                if(votes >= votesNeeded) {
                    audio.player.stopTrack()
                    embed {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription("Skipped **${track.info.title}**.")
                    }
                } else {
                    embed {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription("Voted to skip **${track.info.title}**. ($votes/$votesNeeded votes)")
                    }
                }.block()
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
                    error("There are no tracks currently playing.").block()
                    return@discord
                }
                if(canFSkip(this, track)) {
                    audio.player.stopTrack()
                    embed {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription("Force-skipped **${track.info.title}**.")
                    }
                } else {
                    error {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription("You can not force-skip **${track.info.title}**.")
                    }
                }.block()
            }
        }
    }
}
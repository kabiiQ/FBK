package moe.kabii.command.commands.audio

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.sync.withLock
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.util.Embeds

object QueueSkip : AudioCommandContainer {
    object VoteSkip : Command("skip", "voteskip", "skipsong") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    reply(Embeds.error("There is no track currently playing.")).awaitSingle()
                    return@discord
                }
                if(config.musicBot.autoFSkip && canFSkip(this, track)) {
                    audio.player.stopTrack()
                    reply(Embeds.fbk("Force-skipped **${track.info.title}**.")).awaitSingle()
                    return@discord
                }
                if(!canVoteSkip(this, track)) {
                    reply(Embeds.error("You must be in the bot's voice channel to vote skip.")).awaitSingle()
                    return@discord
                }
                val data = track.userData as QueueData
                val votesNeeded = getSkipsNeeded(this)
                val votes = data.voting.withLock {
                    if (data.votes.contains(author.id)) {
                        val votes = data.votes.count()
                        reply(Embeds.error("You have already voted to skip **${track.info.title}**. ($votes/$votesNeeded votes)")).awaitSingle()
                        return@discord
                    }
                    data.votes.add(author.id)
                    data.votes.count()
                }
                if(votes >= votesNeeded) {
                    reply(Embeds.fbk("Skipped **${track.info.title}**.")).awaitSingle()
                    audio.player.stopTrack()
                } else {
                    reply(Embeds.fbk("Voted to skip **${track.info.title}**. ($votes/$votesNeeded votes)")).awaitSingle()
                }
            }
        }
    }

    object ForceSkip : Command("fskip", "forceskip", "fskipsong", "skipf") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            discord {
                channelFeatureVerify(FeatureChannel::musicChannel)
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val track = audio.player.playingTrack
                if(track == null) {
                    reply(Embeds.error("There are no tracks currently playing.")).awaitSingle()
                    return@discord
                }
                if(canFSkip(this, track)) {
                    audio.player.stopTrack()
                    reply(Embeds.fbk("Force-skipped **${track.info.title}**."))
                } else {
                    reply(Embeds.fbk("You can not force-skip **${track.info.title}**."))
                }.awaitSingle()
            }
        }
    }
}
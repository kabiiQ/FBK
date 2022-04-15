package moe.kabii.command.commands.audio

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.sync.withLock
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.QueueData
import moe.kabii.discord.util.Embeds

object TrackSkip : AudioCommandContainer {
    suspend fun skip(origin: DiscordParameters, silent: Boolean = false) = with(origin) {
        channelFeatureVerify(FeatureChannel::musicChannel)
        val audio = AudioManager.getGuildAudio(target.id.asLong())
        val track = audio.player.playingTrack
        if(track == null) {
            ereply(Embeds.error("There is no track currently playing.")).awaitSingle()
            return@with
        }
        if(config.musicBot.autoFSkip && canFSkip(this, track)) {
            audio.player.stopTrack()
            if(!silent) {
                ireply(Embeds.fbk("Force-skipped **${track.info.title}**.")).awaitSingle()
            }
            return@with
        }
        if(!canVoteSkip(this, track)) {
            ereply(Embeds.error("You must be in the bot's voice channel to vote skip.")).awaitSingle()
            return@with
        }
        val data = track.userData as QueueData
        val votesNeeded = getSkipsNeeded(this)
        val votes = data.voting.withLock {
            if (data.votes.contains(author.id)) {
                val votes = data.votes.count()
                ereply(Embeds.error("You have already voted to skip **${track.info.title}**. ($votes/$votesNeeded votes)")).awaitSingle()
                return@with
            }
            data.votes.add(author.id)
            data.votes.count()
        }
        if(votes >= votesNeeded) {
            ireply(Embeds.fbk("Skipped **${track.info.title}**.")).awaitSingle()
            audio.player.stopTrack()
        } else {
            ireply(Embeds.fbk("Voted to skip **${track.info.title}**. ($votes/$votesNeeded votes)")).awaitSingle()
        }
    }

    object SkipCommand : Command("skip") {
        override val wikiPath = "Music-Player#queue-manipulation"

        init {
            chat {
                skip(this)
            }
        }
    }
}
package moe.kabii.discord.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.runBlocking
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.util.BotUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait
import moe.kabii.util.extensions.tryBlock

object AudioEventHandler : AudioEventAdapter() {
    val manager = AudioManager

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        val data = track.userData as QueueData
        player.volume = data.volume

        // apply this track's audio filters. always reset filter factory to empty if there are no filters applied
        player.setFilterFactory(data.audioFilters.export())

        val originChan = data.fbk.client.getChannelById(data.originChannel)
            .ofType(GuildMessageChannel::class.java)

        val guildID = data.audio.guildId
        val config = GuildConfigurations.getOrCreateGuild(data.fbk.clientId, guildID).musicBot
        // guild option to skip song if queuer has left voice channel.
        if(config.skipIfAbsent) {
            val vc = data.fbk.client.getGuildById(guildID.snowflake)
                .flatMap(BotUtil::getBotVoiceChannel)
                .tryBlock().orNull()
            if(vc != null) {
                val userPresent = vc.voiceStates
                    .filter { state -> state.userId == data.author }
                    .hasElements().tryBlock().orNull()
                if(userPresent == false) {  // abandon this if it errors, but the bot should definitely be in a voice channel if this is reached
                    originChan.flatMap { chan ->
                        val title = AudioCommandContainer.trackString(track, includeAuthor = false)
                        val author = data.author_name
                        chan.createMessage(
                            Embeds.fbk("Skipping **$title** because **$author** left the channel.")
                        )
                    }.subscribe()
                    track.stop()
                    return
                }
            }
        }
        // post message when song starts playing if it was a user action
        if(!data.apply) {
            originChan
                .flatMap { chan ->
                    val paused = if(player.isPaused) "\n\n**The bot is currently paused.**" else ""
                    val title = AudioCommandContainer.trackString(track)
                    val now = if(track.position > 0) "Resuming" else "Now playing"
                    val looping = if(data.audio.looping) "\n\n**The queue is currently configured to loop tracks.**" else ""
                    chan.createMessage(
                        Embeds.fbk("$now **$title**. $paused$looping")
                            .run { if(track is YoutubeAudioTrack) withThumbnail(URLUtil.StreamingSites.Youtube.thumbnail(track.identifier)) else this }
                    )
                }.doOnNext { msg ->
                    data.associatedMessages.add(QueueData.NowPlaying(msg.channelId, msg.id))
                }.subscribe()
        } else {
            data.apply = false
        }

        // if the bot is not alone when something starts playing, cancel any inactivity timeouts
        val alone = runBlocking {
            data.fbk.client.getGuildById(guildID.snowflake)
                .flatMap(BotUtil::getBotVoiceChannel)
                .flatMap(BotUtil::isSingleClient)
                .tryAwait().orNull()
        }
        if(alone != true) {
            data.audio.discord.cancelPendingTimeout()
        }
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        val data = track.userData as QueueData
        when(endReason) {
            AudioTrackEndReason.FINISHED, AudioTrackEndReason.LOAD_FAILED, AudioTrackEndReason.STOPPED -> {
                if (data.audio.ending) return
                if(endReason == AudioTrackEndReason.STOPPED && data.apply) { // restarting playback to apply audio filters
                    val new = track.makeClone().apply {
                        position = track.position
                        userData = data
                    }
                    player.playTrack(new)
                    return
                }

                // if queue is set to loop, add this track to the end of the queue
                if(data.audio.looping && endReason != AudioTrackEndReason.STOPPED) {
                    val newTrack = track.makeClone()
                    newTrack.userData = data.apply {
                        votes.clear()
                        silent = true
                    }
                    runBlocking { data.audio.forceAdd(newTrack) }
                }

                data.audio.editQueueSync { // need to save queue even if there is no next track
                    if (data.audio.queue.isNotEmpty()) {
                        val next = removeAt(0)
                        player.playTrack(next)
                    } else {
                        // no more tracks being added, start timeout for leaving vc
                        data.audio.discord.startTimeout()
                        if(data.audio.looping) {
                            data.audio.looping = false
                        }
                    }
                }

                // delete old messages per guild settings
                val guildID = data.audio.guildId
                val config = GuildConfigurations.getOrCreateGuild(data.fbk.clientId, guildID)
                data.associatedMessages.forEach { msg ->
                    data.fbk.client.getMessageById(msg.channelID, msg.messageID)
                        .flatMap { discordMsg ->
                            when(msg) {
                                is QueueData.NowPlaying -> {
                                    if(config.musicBot.deleteNowPlaying) discordMsg.delete("old music bot now playing message")
                                    else discordMsg.addReaction(ReactionEmoji.unicode(EmojiCharacters.checkBox))
                                }
                                is QueueData.Queue -> {
                                    discordMsg.addReaction(ReactionEmoji.unicode(EmojiCharacters.checkBox))
                                }
                            }
                        }.subscribe()
                }
            }
            else -> return
        }
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        val data = track.userData as QueueData
        data.fbk.client.getChannelById(data.originChannel)
            .ofType(GuildMessageChannel::class.java)
            .flatMap { chan ->
                val title = AudioCommandContainer.trackString(track)
                chan.createMessage(
                    Embeds.error()
                        .withTitle("An error occured during audio playback")
                        .withFields(mutableListOf(
                            EmbedCreateFields.Field.of("Track", title, false),
                            EmbedCreateFields.Field.of("Error", exception.message ?: "broken", false)
                        ))
                )
            }.subscribe()
    }

    // if a track emits no frames for 10 seconds we will skip it
    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) =
        onTrackEnd(player, track, AudioTrackEndReason.LOAD_FAILED)
}
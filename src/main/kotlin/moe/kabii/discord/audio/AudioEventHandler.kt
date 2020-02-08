package moe.kabii.discord.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import discord4j.core.`object`.entity.TextChannel
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.commands.audio.AudioCommandContainer
import moe.kabii.discord.command.errorColor
import moe.kabii.discord.command.kizunaColor
import moe.kabii.discord.util.BotUtil
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import moe.kabii.util.YoutubeUtil
import reactor.core.publisher.toFlux

object AudioEventHandler : AudioEventAdapter() {
    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        val data = track.userData as QueueData
        player.volume = data.volume

        // apply this track's audio filters. always reset filter factory to empty if there are no filters applied
        player.setFilterFactory(data.audioFilters.export())

        val originChan = data.discord.getChannelById(data.originChannel)
            .ofType(TextChannel::class.java)

        val guildID = data.audio.guild
        val config = GuildConfigurations.getOrCreateGuild(guildID).musicBot
        // guild option to skip song if queuer has left voice channel.
        if(config.skipIfAbsent) {
            val chan = data.discord.getGuildById(guildID.snowflake)
                .flatMap(BotUtil::getBotVoiceChannel)
                .tryBlock().orNull()
            if(chan != null) {
                val userPresent = chan.voiceStates.filter { state -> state.userId == data.author }.hasElements().tryBlock().orNull()
                if(userPresent == false) {  // abandon this if it errors, but the bot should definitely be in a voice channel if this is reached
                    originChan.flatMap { chan ->
                        chan.createEmbed { embed ->
                            val title = AudioCommandContainer.trackString(track, includeAuthor = false)
                            val author = data.author_name
                            kizunaColor(embed)
                            embed.setDescription("Skipping **$title** because **$author** left the channel.")
                        }
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
                    val paused = if(player.isPaused) "The bot is currently paused." else ""
                    chan.createEmbed { embed ->
                        val title = AudioCommandContainer.trackString(track)
                        kizunaColor(embed)
                        val now = if(track.position > 0) "Resuming" else "Now playing"
                        embed.setDescription("$now **$title**. $paused")
                        if(track is YoutubeAudioTrack) embed.setThumbnail(YoutubeUtil.thumbnailUrl(track.identifier))
                    }
                }.map { np ->
                    QueueData.BotMessage.NPEmbed(np.channelId, np.id)
                }.subscribe { np -> data.associatedMessages.add(np) }
        } else {
            data.apply = false
            val filters = data.audioFilters.asString()
            originChan.flatMap { chan ->
                chan.createEmbed { embed ->
                    kizunaColor(embed)
                    embed.setDescription("Applying filters:\n\n$filters")
                }
            }.subscribe()
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

                data.audio.editQueueSync { // need to save queue even if there is no next track
                    if (data.audio.queue.isNotEmpty()) {
                        val next = removeAt(0)
                        player.playTrack(next)
                    }
                }
                // delete old messages per guild settings
                val guildID = data.audio.guild
                val config = GuildConfigurations.getOrCreateGuild(guildID)
                data.associatedMessages.toFlux().filter { msg ->
                    when(msg) {
                        is QueueData.BotMessage.NPEmbed, is QueueData.BotMessage.TrackQueued -> config.musicBot.deleteOldBotMessages
                        is QueueData.BotMessage.UserPlayCommand -> config.musicBot.deleteUserCommands
                    }
                }.flatMap { msg ->
                    data.discord.getMessageById(msg.channelID, msg.messageID)
                }.flatMap { message -> message.delete("Old music bot command")
                }.subscribe()
            }
        }
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        val data = track.userData as QueueData
        data.discord.getChannelById(data.originChannel)
            .ofType(TextChannel::class.java)
            .flatMap { chan ->
                chan.createEmbed { embed ->
                    val title = AudioCommandContainer.trackString(track)
                    errorColor(embed)
                    embed.setTitle("An error occured during audio playback")
                    embed.addField("Track", title, false)
                    embed.addField("Error", exception.message, false)
                }
            }.subscribe()
    }

    // if a track emits no frames for 10 seconds we will skip it
    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) =
        onTrackEnd(player, track, AudioTrackEndReason.LOAD_FAILED)
}
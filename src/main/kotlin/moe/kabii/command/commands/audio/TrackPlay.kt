package moe.kabii.command.commands.audio

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.hasPermissions
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.*
import moe.kabii.discord.util.Embeds
import moe.kabii.net.api.commands.ExternalCommand
import moe.kabii.util.extensions.awaitAction

object TrackPlay : AudioCommandContainer {
    object PlaySong : Command("play") {
        override val wikiPath: String? = null

        init {
            chat {
                channelFeatureVerify(FeatureChannel::musicChannel)
                event.deferReply().awaitAction()
                val voice = AudioStateUtil.checkAndJoinVoice(this)
                if(voice is AudioStateUtil.VoiceValidation.Failure) {
                    event.editReply()
                        .withEmbeds(Embeds.error(voice.error))
                        .awaitSingle()
                    return@chat
                }
                // grab attachment or "song"
                val query = ExtractedQuery.from(this)

                when {
                    args.optBool("forceplay") == true -> {
                        channelVerify(Permission.MANAGE_MESSAGES)
                        if(!member.hasPermissions(guildChan, Permission.MANAGE_MESSAGES)) {
                            event.editReply()
                                .withEmbeds(Embeds.error(i18n("audio_force_play_denied")))
                                .awaitSingle()
                            return@chat
                        }
                        AudioManager.manager.loadItem(query.url, ForcePlayTrackLoader(this, query))
                    }
                    args.optBool("playlist") == true -> {
                        val songArg = args.string("song")
                        val playlist = ExtractedQuery.default(songArg)
                        AudioManager.manager.loadItem(songArg, PlaylistTrackLoader(this, extract = playlist))
                    }
                    else -> {
                        // adds a song to the end of queue (front if next=true)
                        val playNextArg = args.optBool("next")
                        val position = if(playNextArg == true) 0 else null // default (null) -> false
                        AudioManager.manager.loadItem(query.url, SingleTrackLoader(this, position, query))
                    }
                }
            }

            extern {
                val playCommand = command as ExternalCommand.Play
                val query = playCommand.query
                val targetVoice = fbk.client
                    .getChannelById(Snowflake.of(playCommand.voiceChannelId))
                    .ofType(VoiceChannel::class.java)
                    .awaitSingle()

                val audio = AudioManager.getGuildAudio(fbk, targetVoice.guildId.asLong())
                audio.joinChannel(targetVoice).unwrap()

                AudioManager.manager.loadItem("ytsearch: $query", ExternalSimpleTrackLoader(this, audio, query))
            }
        }
    }
}
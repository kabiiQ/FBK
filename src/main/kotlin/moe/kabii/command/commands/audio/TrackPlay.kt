package moe.kabii.command.commands.audio

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.hasPermissions
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.*
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.awaitAction

object TrackPlay : AudioCommandContainer {
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
                // grab attachment or "song"
                val query = ExtractedQuery.from(this)

                when {
                    args.optBool("forceplay") == true -> {
                        channelVerify(Permission.MANAGE_MESSAGES)
                        if(!member.hasPermissions(guildChan, Permission.MANAGE_MESSAGES)) {
                            ereply(Embeds.error("You must be a channel moderator to force-play tracks.")).awaitSingle()
                            return@discord
                        }
                        event.deferReply().awaitAction()
                        AudioManager.manager.loadItem(query.url, ForcePlayTrackLoader(this, query))
                    }
                    args.optBool("playlist") == true -> {
                        event.deferReply().awaitAction()
                        val songArg = args.string("song")
                        val playlist = ExtractedQuery.default(songArg)
                        AudioManager.manager.loadItem(songArg, PlaylistTrackLoader(this, extract = playlist))
                    }
                    else -> {
                        event.deferReply().awaitAction()
                        // adds a song to the end of queue (front if next=true)
                        val playNextArg = args.optBool("playnext")
                        val position = if(playNextArg == true) 0 else null // default (null) -> false
                        AudioManager.manager.loadItem(query.url, SingleTrackLoader(this, position, query))
                    }
                }
            }
        }
    }
}
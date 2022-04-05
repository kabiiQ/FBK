package moe.kabii.command.commands.moderation

import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioStateUtil
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.TempStates
import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.util.Embeds
import moe.kabii.util.constants.EmojiCharacters
import reactor.core.publisher.Mono

object Drag : Command("drag") {
    override val wikiPath = "Moderation-Commands#mass-drag-users-in-voice-channels"

    init {
        discord {
            member.verify(Permission.MOVE_MEMBERS)
            val args = subArgs(subCommand)
            val toArg = args.channel("to", VoiceChannel::class.java).awaitSingle()
            val operation = when(subCommand.name) {
                "all" -> dragUsers(this, null, toArg)
                "between" -> {
                    val fromArg = args.channel("from", VoiceChannel::class.java).awaitSingle()
                    dragUsers(this, fromArg, toArg)
                }
                else -> error("subcommand mismatch")
            }
            operation.blockLast()
        }
    }

    private suspend fun dragUsers(origin: DiscordParameters, from: VoiceChannel?, to: VoiceChannel) =
        origin.target.voiceStates
            .run { if(from != null) filter { state ->
                state.channelId.get() == from.id
            } else this }
            .flatMap(VoiceState::getUser)
            .flatMap { user -> user.asMember(origin.target.id) }
            .flatMap { member ->
                member
                    .edit()
                    .withNewVoiceChannelOrNull(to.id)
            }
            .onErrorResume { _ -> Mono.empty() }
}
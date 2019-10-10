package moe.kabii.discord.command.commands.moderation

import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.util.Permission
import moe.kabii.data.TempStates
import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.verify
import moe.kabii.discord.conversation.ReactionInfo
import moe.kabii.discord.conversation.ReactionListener
import moe.kabii.util.EmojiCharacters

object Drag : Command("drag", "move", "pull") {
    init {
        discord {
            when (args.getOrNull(0)) {
                // drag all pulls all users into user channel
                "all" -> {
                    member.verify(Permission.MANAGE_CHANNELS)
                    val targetChannel = member.voiceState.flatMap(VoiceState::getChannel).block()
                    if (targetChannel != null) {
                        embed("Moving all users to **${targetChannel.name}**.").subscribe()
                        target.voiceStates
                                .flatMap(VoiceState::getUser)
                                .flatMap { user -> user.asMember(target.id) }
                                .flatMap { member ->
                                    member.edit { spec ->
                                        spec.setNewVoiceChannel(targetChannel.id)
                                    }
                                }.onErrorContinue { _, _ -> }
                                .blockLast()
                    } else {
                        error("**drag all** moves ALL users in this server's voice channels to your current voice channel. You must be in a voice channel that I can move users into, to use the command.").block()
                    }
                }
                // normal drag command pulls users along with the bot when it moves
                else -> {
                    member.verify(Permission.MOVE_MEMBERS)
                    with (TempStates.dragGuilds) {
                        if (contains(target.id)) {
                            remove(target.id)
                            embed("Drag operation cancelled.").subscribe()
                        } else {
                            add(target.id)
                            val message = embed("Ready! Move me to drag any users in my voice channel along with me.").block()
                            ReactionListener(
                                    MessageInfo.of(message),
                                    listOf(
                                            ReactionInfo(EmojiCharacters.cancel, "cancel")
                                    ),
                                    author.id.asLong(),
                                    event.client
                            ) { _, _, _ ->
                                if (contains(target.id)) {
                                    remove(target.id)
                                    message.delete().subscribe()
                                    embed("Drag operation cancelled.").subscribe()
                                }
                                true
                            }.create(message, add = true)
                        }
                    }
                }
            }
        }
    }
}
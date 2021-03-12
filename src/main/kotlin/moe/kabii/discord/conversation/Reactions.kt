package moe.kabii.discord.conversation

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.util.extensions.UserID
import moe.kabii.util.extensions.success
import moe.kabii.util.extensions.tryBlock
import reactor.core.publisher.Flux

typealias Complete = Boolean

object ReactionManager {
    val listeners = mutableListOf<ReactionListener>()
}

class ReactionListener(val messageInfo: MessageInfo,
                       val reactions: List<ReactionInfo>,
                       val user: Long?,
                       val discord: GatewayDiscordClient,
                       val listenRemove: Boolean = true,
                       val callback: (ReactionInfo, UserID, Conversation?) -> Complete) {

    fun create(message: Message, add: Boolean): Boolean {
        with(ReactionManager.listeners) {
            if(none { listener -> listener.messageInfo == messageInfo }) {
                add(this@ReactionListener)
                if(add) {
                    message.removeAllReactions().success().tryBlock()
                    Flux.fromIterable(reactions)
                        .flatMap { reaction -> message.addReaction(ReactionEmoji.unicode(reaction.unicode)) }
                        .blockLast()
                }
            }

        }
        return true
    }

    fun cancel() {
        ReactionManager.listeners.remove(this)
        // remove reactions/string if listener is cancelled
    }
}

data class ReactionInfo(val unicode: String, val name: String)
package moe.kabii.command.commands.moderation

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.filterNot
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.snowflake
import reactor.core.publisher.Flux

object Purge : CommandContainer {
    const val SMALL_MESSAGEID = 100_000_000_000_000_000L

    private suspend fun purgeAndNotify(origin: DiscordParameters, messages: Flux<Message>) {
        origin.chan as GuildMessageChannel
        var messageCount = 0
        val users = mutableSetOf<Snowflake>()

        val skipped = messages
            .filterNot(Message::isPinned)
            .doOnNext { message ->
                users.add(message.author.get().id)
                messageCount++
            }
            .map(Message::getId)
            .transform { origin.chan.bulkDelete(it) } // returns messages which could not be bulk deleted
            .collectList()
            .awaitSingle()
        val warnSkip = if(skipped.isNotEmpty()) " ${skipped.size} messages were skipped as they were [too old](https://github.com/discord/discord-api-docs/issues/208) to be purged." else ""
        origin.ireply(Embeds.fbk("Deleted $messageCount messages from ${users.size} users.$warnSkip")).awaitSingle()
    }

    object PurgeCount : Command("purge") {
        override val wikiPath = "Purge-Messages#purging-by-specifying-the-number-of-messages-to-delete-purge"

        init {
            botReqs(Permission.MANAGE_MESSAGES)
            discord {
                // /purge <count>
                val last = interaction.channel.awaitSingle().lastMessageId.orNull()
                if(last == null) {
                    ereply(Embeds.error("No messages to purge!")).awaitSingle()
                    return@discord
                }
                channelVerify(Permission.MANAGE_MESSAGES)
                val messageCount = args.int("count")
                val delete = chan
                    .getMessagesBefore(last)
                    .take(messageCount)
                purgeAndNotify(this, delete)
            }
        }
    }

    object PurgeFrom : Command("purgefrom") {
        override val wikiPath = "Purge-Messages#purging-by-specifying-the-first-message-id-to-delete-purgefrom"

        init {
            botReqs(Permission.MANAGE_MESSAGES)
            discord {
                channelVerify(Permission.MANAGE_MESSAGES)
                val startMessage = args.string("start").toLongOrNull()
                if(startMessage == null || startMessage < SMALL_MESSAGEID) {
                    ereply(Embeds.error("Invalid beginning message ID **$startMessage**.")).awaitSingle()
                    return@discord
                }
                val endMessage = args.optStr("end")?.toLongOrNull()
                if(endMessage != null && (endMessage < SMALL_MESSAGEID || endMessage < startMessage)) {
                    ereply(Embeds.error("Invalid ending message ID **$endMessage**")).awaitSingle()
                    return@discord
                }

                val delete = chan
                    .getMessagesAfter(startMessage.snowflake)
                    .run { if(endMessage != null) takeUntil { message ->
                        message.id >= endMessage.snowflake
                    } else this }

                purgeAndNotify(this, delete)
            }
        }
    }
}
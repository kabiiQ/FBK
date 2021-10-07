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
import moe.kabii.util.extensions.snowflake
import reactor.core.publisher.Flux

object Purge : CommandContainer {
    const val SMALL_MESSAGEID = 100_000_000_000_000_000L

    private suspend fun purgeAndNotify(origin: DiscordParameters, userArgs: List<String>, messages: Flux<Message>) {
        origin.chan as GuildMessageChannel
        val limitUsers = userArgs
            .mapNotNull(String::toLongOrNull)
            .map(Long::snowflake)
        fun shouldPurge(user: Snowflake): Boolean = limitUsers.isEmpty() || limitUsers.contains(user)
        var messageCount = 0
        val users = mutableSetOf<Snowflake>()

        val skipped = messages
            .filterNot(Message::isPinned)
            .filter { message -> shouldPurge(message.author.get().id) }
            .doOnNext { message ->
                users.add(message.author.get().id)
                messageCount++
            }
            .map(Message::getId)
            .transform { origin.chan.bulkDelete(it) } // returns messages which could not be bulk deleted
            .collectList()
            .awaitSingle()
        val warnSkip = if(skipped.isNotEmpty()) " ${skipped.size} messages were skipped as they were [too old](https://github.com/discord/discord-api-docs/issues/208) to be purged." else ""
        origin.reply(Embeds.fbk("Deleted $messageCount messages from ${users.size} users.$warnSkip")).awaitSingle()
    }

    object PurgeCount : Command("purge", "clean", "prune") {
        override val wikiPath = "Purge-Messages#purging-by-specifying-the-number-of-messages-to-delete-purge"

        init {
            botReqs(Permission.MANAGE_MESSAGES)
            discord {
                // ;purge 20 <users to limit to, else all>=
                channelVerify(Permission.MANAGE_MESSAGES)
                if(args.isEmpty()) {
                    usage("**purge** will delete the specified number of messages. A user ID can also be provided to only remove the messages from specific users.",
                        "purge <message count> (optional user ID)")
                    return@discord
                }
                val messageCount = args[0].toShortOrNull()
                if(messageCount == null) {
                    reply(Embeds.error("Invalid message count **${args[0]}**.")).awaitSingle()
                    return@discord
                }

                val delete = chan.getMessagesBefore(event.message.id)
                    .take(messageCount.toLong())
                purgeAndNotify(this, args.drop(1), delete) }
        }
    }

    object PurgeFrom : Command("purgefrom", "purgeafter", "cleanfrom", "cleanafter", "prunefrom", "pruneafter") {
        override val wikiPath = "Purge-Messages#purging-by-specifying-the-first-message-id-to-delete-purgefrom"

        init {
            botReqs(Permission.MANAGE_MESSAGES)
            discord {
                channelVerify(Permission.MANAGE_MESSAGES)
                if(args.isEmpty()) {
                    usage("**purgefrom** will delete messages after the provided message ID.", "purgefrom <start message ID> (optional user IDs)").awaitSingle()
                    return@discord
                }
                val startMessage = args[0].toLongOrNull()?.minus(1)?.snowflake
                if(startMessage == null || startMessage.asLong() < SMALL_MESSAGEID) {
                    reply(Embeds.error("Invalid beginning message ID **${args[0]}**.")).awaitSingle()
                    return@discord
                }

                val delete = chan.getMessagesAfter((startMessage.asLong()).snowflake)
                purgeAndNotify(this, args.drop(1), delete)
            }
        }
    }

    object PurgeBetween : Command("purgebetween", "cleanbetween", "prunebetween") {
        override val wikiPath = "Purge-Messages#purging-by-specifying-the-first-and-last-messages-to-delete-purgebetween"

        init {
            botReqs(Permission.MANAGE_MESSAGES)
            discord {
                // purgebetween begin end users
                channelVerify(Permission.MANAGE_MESSAGES)
                if(args.size < 2) {
                    usage("**purgebetween** will delete messages between two provided message IDs. For simple purging of recent messages see **purge** or **purgefrom**.",
                        "purgebetween <beginning message ID> <ending message ID> (optional user IDs)").awaitSingle()
                    return@discord
                }
                val startMessage = args[0].toLongOrNull()?.minus(1)?.snowflake
                val endMessage = args[1].toLongOrNull()?.snowflake
                if(startMessage == null || endMessage == null
                    || startMessage.asLong() < SMALL_MESSAGEID || endMessage.asLong() < SMALL_MESSAGEID
                    || startMessage > endMessage) {
                    reply(Embeds.error("Invalid purge range between **${args[0]}** and **${args[1]}**.")).awaitSingle()
                    return@discord
                }

                val delete = chan.getMessagesAfter(startMessage)
                    .takeUntil { message -> message.id >= endMessage }
                purgeAndNotify(this, args.drop(2), delete)
                event.message.delete().awaitSingle()
            }
        }
    }
}
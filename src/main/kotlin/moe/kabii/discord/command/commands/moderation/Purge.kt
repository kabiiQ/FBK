package moe.kabii.discord.command.commands.moderation

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.Snowflake
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.command.channelVerify
import moe.kabii.structure.filterNot
import moe.kabii.structure.snowflake
import reactor.core.publisher.Flux
import reactor.core.publisher.toFlux

object Purge : CommandContainer {
    const val SMALL_MESSAGEID = 100_000_000_000_000_000L

    private fun purgeAndNotify(origin: DiscordParameters, userArgs: List<String>, messages: Flux<Message>) {
        origin.chan as TextChannel
        val limitUsers = userArgs
            .mapNotNull(String::toLongOrNull)
            .map(Long::snowflake)
        fun purgeUser(user: Snowflake) = limitUsers.isEmpty() || limitUsers.contains(user)
        var messageCount = 0
        val users = mutableListOf<Snowflake>()

        messages
            .filterNot(Message::isPinned)
            .filter { message -> purgeUser(message.author.get().id) }
            .doOnNext { message ->
                users.add(message.author.get().id)
                messageCount++
            }
            .map(Message::getId)
            .compose(origin.chan::bulkDelete) // returns messages which could not be bulk deleted
            .take(25) // arbitrary limit for manual one-by-one deletes
            .flatMap(origin.chan::getMessageById)
            .flatMap(Message::delete)
            .collectList()
            .block()
        val userCount = users.toFlux()
            .distinct(Snowflake::hashCode)
            .count().block()
        origin.embed("Deleted $messageCount messages from $userCount users.").block()
    }

    object PurgeCount : Command("purge", "clean", "prune") {
        init {
            botReqs(Permission.MANAGE_MESSAGES)
            discord {
                // ;purge 20 <users to limit to, else all>=
                member.channelVerify(chan as TextChannel, Permission.MANAGE_MESSAGES)
                if(args.isEmpty()) {
                    usage("**purge** will delete the specified number of messages. A user ID can also be provided to only remove the messages from specific users.",
                        "purge <message count> (optional user ID)")
                    return@discord
                }
                val messageCount = args[0].toShortOrNull()
                if(messageCount == null) {
                    error("Invalid message count **${args[0]}**.").block()
                    return@discord
                }

                val delete = chan.getMessagesBefore(event.message.id)
                    .take(messageCount.toLong())
                purgeAndNotify(this, args.drop(1), delete) }
        }
    }

    object PurgeFrom : Command("purgefrom", "purgeafter", "cleanfrom", "cleanafter", "prunefrom", "pruneafter") {
        init {
            botReqs(Permission.MANAGE_MESSAGES)
            discord {
                member.channelVerify(chan as TextChannel, Permission.MANAGE_MESSAGES)
                if(args.isEmpty()) {
                    usage("**purgefrom** will delete messages after the provided message ID.", "purgefrom <start message ID> (optional user IDs)").block()
                    return@discord
                }
                val startMessage = args[0].toLongOrNull()?.minus(1)?.snowflake
                if(startMessage == null || startMessage.asLong() < SMALL_MESSAGEID) {
                    error("Invalid beginning message ID **${args[0]}**.").block()
                    return@discord
                }

                val delete = chan.getMessagesAfter((startMessage.asLong()).snowflake)
                purgeAndNotify(this, args.drop(1), delete)
            }
        }
    }

    object PurgeBetween : Command("purgebetween", "cleanbetween", "prunebetween") {
        init {
            botReqs(Permission.MANAGE_MESSAGES)
            discord {
                // purgebetween begin end users
                member.channelVerify(chan as TextChannel, Permission.MANAGE_MESSAGES)
                if(args.size < 2) {
                    usage("**purgebetween** will delete messages between two provided message IDs. For simple purging of recent messages see **purge** or **purgefrom**.",
                        "purgebetween <beginning message ID> <ending message ID> (optional user IDs)").block()
                    return@discord
                }
                val startMessage = args[0].toLongOrNull()?.minus(1)?.snowflake
                val endMessage = args[1].toLongOrNull()?.snowflake
                if(startMessage == null || endMessage == null
                    || startMessage.asLong() < SMALL_MESSAGEID || endMessage.asLong() < SMALL_MESSAGEID
                    || startMessage > endMessage) {
                    error("Invalid purge range between **${args[0]}** and **${args[1]}**.").block()
                    return@discord
                }

                val delete = chan.getMessagesAfter(startMessage)
                    .takeUntil { message -> message.id >= endMessage }
                purgeAndNotify(this, args.drop(2), delete)
                event.message.delete().block()
            }
        }
    }
}
package moe.kabii.command.commands.admin

import discord4j.core.`object`.entity.channel.MessageChannel
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verifyBotAdmin
import moe.kabii.data.relational.posts.twitter.NitterFeed
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.propagateTransaction

object AdminRemoveCommand : Command("remove") {
    override val wikiPath: String? = null

    init {
        chat {
            event.verifyBotAdmin()
            when(subCommand.name) {
                "twitter" -> removeFeed(this)
            }
        }
    }

    private suspend fun removeFeed(origin: DiscordParameters) = with(origin) {
        val feedName = subArgs(subCommand).string("feed")
        // Find Twitter feed in database
        val dbFeed = propagateTransaction {
            NitterFeed.findExisting(feedName)?.feed
        }
        if (dbFeed == null) {
            ereply(Embeds.error("Twitter feed **$feedName** not found by name.")).awaitSingle()
            return@with
        }

        val twitter = handler.instances.services.twitterChecker
        val targets = twitter.getActiveTargets(dbFeed)

        ereply(Embeds.fbk("Twitter feed **$feedName** deleted completely. Notifications now being sent.")).awaitSingle()

        // send notification to active feed channels
        targets?.forEach { target ->
            try {

                val client = handler.instances[target.discordClient].client
                val channel = client.getChannelById(target.discordChannel)
                    .ofType(MessageChannel::class.java)
                    .awaitSingle()
                channel.createMessage(
                    Embeds.fbk("Twitter feed **@$feedName** which is tracked in this channel is being deleted from the bot. This is likely due to **$feedName** experiencing a Twitter suspension/ban, going private, or changing names (if this is the case, you can `/track` with the new name, the bot is not able to follow name changes automatically).")
                ).awaitSingle()

            } catch (e: Exception) {
                LOG.warn("Unable to send feed untrack message to $target")
            }
        }

        // delete feed regardless of notifications success and state
        propagateTransaction {
            dbFeed.delete()
        }
    }
}
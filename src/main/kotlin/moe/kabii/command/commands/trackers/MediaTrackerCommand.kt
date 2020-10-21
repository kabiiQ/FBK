package moe.kabii.command.commands.trackers

import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.FeatureDisabledException
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.discord.trackers.AnimeTarget
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.discord.trackers.anime.MediaListDeletedException
import moe.kabii.discord.trackers.anime.MediaListIOException
import moe.kabii.discord.util.errorColor
import moe.kabii.discord.util.fbkColor
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.tryAwait
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object MediaTrackerCommand {
    suspend fun track(origin: DiscordParameters, target: TargetArguments) {
        // if this is in a guild make sure the media list feature is enabled here
        val config = origin.guild?.run { GuildConfigurations.getOrCreateGuild(id.asLong()) }
        if(config != null) {
            val features = config.options.featureChannels[origin.chan.id.asLong()]
            if(features == null || !features.animeChannel) throw FeatureDisabledException("anime", origin)
        }

        val listName = target.identifier
        val channelId = origin.chan.id.asLong()

        val parser = site.parser
        val listId = parser.getListID(target.identifier)
        if(listId == null) {
            origin.error("Unable to find ${site.full} list with identifier **$listName**.").awaitSingle()
            return
        }
        val targetList = ListInfo(site, listId)
        // if the list is already tracked we need to return early rather than letting io be spammed. weird flow here but saving lots of i/o time. should still be refactored
        val existingTrack = TrackedMediaLists.lock.withLock {
            TrackedMediaLists.mediaLists.find { trackedList -> trackedList.list == targetList }?.targets?.find { target -> target.channelID == channelId }
        }

        if(existingTrack != null) {
            origin.error("**${listName}** is already tracked in this channel.").awaitSingle()
            return
        }

        // jikan can respond very slowly, let user know we're working on this command
        val prompt = origin.embed("Retrieving MAL...").awaitSingle()
        suspend fun editPrompt(spec: (EmbedCreateSpec).() -> Unit) {
            prompt.edit { edit -> edit.setEmbed(spec) }.awaitSingle()
        }

        // validate list
        val request = parser.parse(listId)
        val mediaList = when(request) {
            is Ok -> request.value
            is Err -> {
                when(request.value) {
                    is MediaListEmpty -> {
                        editPrompt {
                            errorColor(this)
                            setDescription("Unable to find ${targetList.site.full} list with identifier **${listName}**.")
                        }
                        return
                    }
                    else -> {
                        editPrompt {
                            errorColor(this)
                            setDescription("Error tracking list! Possible ${targetList.site.full} outage.")
                        }
                        return
                    }
                }
            }
        }
        // track the list if it's not tracked at all
        val find = TrackedMediaLists.lock.withLock {
            TrackedMediaLists.mediaLists.find { it.list == targetList }
        }
        val trackedList = TrackedMediaLists.lock.withLock {
            find ?:
                TrackedMediaList(list = targetList, savedMediaList = mediaList).also { new -> TrackedMediaLists.mediaLists.add(new) }
        }

        // add this channel if the list isn't already tracked in this channel
        // don't just compare MediaTarget because we don't care about WHO tracked the list in this case
        val mediaTarget = MediaTarget(channelId, origin.author.id.asLong())
        trackedList.targets += mediaTarget
        trackedList.save()
        editPrompt {
            fbkColor(this)
            setDescription("Now tracking **$listName**!")
        }
    }

    suspend fun untrack(origin: DiscordParameters, target: TargetArguments) {
        val site = requireNotNull(target.site as? AnimeTarget) { "Invalid target arguments provided to MediaTrackerCommand" }.dbSite
        val parser = site.parser
        val listId = parser.getListID(target.identifier)
        if(listId == null) {
            origin.error("Unable to find ${site.full} list with identifier **${target.identifier}**.").awaitSingle()
            return
        }
        val targetName = target.identifier
        val targetList = ListInfo(site, listId)

        // untrack the list from this location if it's tracked
        val trackedList = TrackedMediaLists.lock.withLock { // find tracked list with matching id/site
            TrackedMediaLists.mediaLists.find { it.list == targetList }
        }

        if(trackedList != null) { // this list is tracked in some channel
            val trackedTarget = trackedList.targets.find { it.channelID == origin.chan.id.asLong()}
            if(trackedTarget != null) { // this list is tracked in this channel
                if(origin.isPM
                        || origin.author.id.asLong() == trackedTarget.discordUserID
                        || origin.event.member.get().hasPermissions(Permission.MANAGE_MESSAGES)) {
                    trackedList.targets -= trackedTarget
                    trackedList.save()
                    origin.embed("No longer tracking **$targetName**.").awaitSingle()
                    return
                } else {
                    val tracker = origin.chan.client.getUserById(trackedTarget.discordUserID.snowflake).tryAwait().orNull()?.username ?: "invalid-user"
                    origin.error("You may not un-track **$targetName** unless you are $tracker or a server moderator.").awaitSingle()
                    return
                }
            }
        }
        origin.error("**$targetName** is not currently being tracked.").awaitSingle()
    }
}
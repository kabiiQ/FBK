package moe.kabii.command.commands.trackers.track

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.AnimeTarget
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.anime.MediaListDeletedException
import moe.kabii.trackers.anime.MediaListIOException
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.tryAwait
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

object MediaTrackerCommand : TrackerCommand {
    override suspend fun track(origin: DiscordParameters, target: TargetArguments, features: FeatureChannel?) {
        // if this is in a guild make sure the media list feature is enabled here
        origin.channelFeatureVerify(FeatureChannel::animeTargetChannel, "anime", allowOverride = false)

        val site = requireNotNull(target.site as? AnimeTarget) { "Invalid target arguments provided to MediaTrackerCommand" }.dbSite
        val siteName = site.targetType.full
        val parser = site.parser
        val inputId = target.identifier

        // this may (ex. for kitsu) or may not (ex. for mal) make a call to find list ID - mal we only will know when we request the full list :/
        val siteListId = parser.getListID(inputId)
        if(siteListId == null) {
            origin.ereply(Embeds.error("Unable to find **$siteName** list with identifier **$inputId**.")).awaitSingle()
            return
        }

        // check if this list is already tracked in this channel, before we download the entire list (can be slow)
        val channelId = origin.chan.id.asLong()
        val existingTrack = transaction {
            TrackedMediaLists.ListTarget.getExistingTarget(origin.client.clientId, site, siteListId.lowercase(), channelId)
        }

        if(existingTrack != null) {
            origin.ereply(Embeds.error("**$siteName/$inputId** is already tracked in this channel.")).awaitSingle()
            return
        }

        TrackerCommandBase.sendTrackerTestMessage(origin)

        origin.ireply(Embeds.fbk("Retrieving **$siteName** list...")).awaitSingle()

        // download and validate list
        val mediaList = try {
            parser.parse(siteListId)
        } catch(delete: MediaListDeletedException) {
            null
        } catch(io: MediaListIOException) {
            LOG.warn("Media list IO issue: ${io.message}")

            origin.event.editReply().withEmbeds(
                Embeds.error("Unable to download your list from **$siteName**: ${io.message}")
            ).awaitSingle()
            return
        } catch(e: Exception) {
            LOG.warn("Caught Exception downloading media list: ${e.message}")
            LOG.trace(e.stackTraceString)

            origin.event.editReply().withEmbeds(
                Embeds.error("Unable to download your list! Possible $siteName outage.")
            ).awaitSingle()
            return
        }

        if(mediaList == null) {
            origin.event.editReply().withEmbeds(
                Embeds.error("Unable to find **$siteName** list with identifier **$inputId**.")
            ).awaitSingle()
            return
        }

        propagateTransaction {

            // track the list if it's not tracked at all, providing downloaded medialist as a base
            val dbList = TrackedMediaLists.MediaList.find {
                TrackedMediaLists.MediaLists.site eq site and
                        (TrackedMediaLists.MediaLists.siteChannelId eq siteListId)
            }.elementAtOrElse(0) { _ ->
                val listJson = mediaList.toDBJson()
                transaction {
                    TrackedMediaLists.MediaList.new {
                        this.site = site
                        this.siteListId = siteListId
                        this.lastListJson = listJson
                    }
                }
            }

            // add this channel as a target for this list's updates, we know this does not exist
            TrackedMediaLists.ListTarget.new {
                this.discordClient = origin.client.clientId
                this.mediaList = dbList
                this.discord = DiscordObjects.Channel.getOrInsert(channelId, origin.guild?.id?.asLong())
                this.userTracked = DiscordObjects.User.getOrInsert(origin.author.id.asLong())
            }
        }

        origin.event.editReply().withEmbeds(
            Embeds.fbk("Now tracking **$inputId** on **$siteName**.")
        ).awaitSingle()
        TargetSuggestionGenerator.updateTargets(origin.client.clientId, origin.chan.id.asLong())
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetArguments) {
        val site = requireNotNull(target.site as? AnimeTarget) { "Invalid target arguments provided to MediaTrackerCommand" }.dbSite
        val siteName = site.targetType.full
        val parser = site.parser
        val inputId = target.identifier

        val siteListId = parser.getListID(inputId)
        if(siteListId == null) {
            origin.ereply(Embeds.error("Unable to find $siteName list with identifier **${target.identifier}**.")).awaitSingle()
            return
        }

        val channelId = origin.chan.id.asLong()

        propagateTransaction {
            val existingTrack = TrackedMediaLists.ListTarget.getExistingTarget(origin.client.clientId, site, siteListId.lowercase(), channelId)
            if (existingTrack == null) {
                origin.ereply(Embeds.error("**$inputId** is not currently being tracked on $siteName.")).awaitSingle()
                return@propagateTransaction
            }

            if(origin.isPM // always allow untrack in pm
                    || origin.author.id.asLong() == existingTrack.userTracked.userID // not in pm, check for same user as tracker
                    || origin.interaction.member.get().hasPermissions(origin.guildChan, Permission.MANAGE_MESSAGES)) { // or channel moderator

                existingTrack.delete()
                origin.ireply(Embeds.fbk("No longer tracking **$inputId** on **$siteName**.")).awaitSingle()
                TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, origin.chan.id.asLong())

            } else {
                val tracker = origin.event.client.getUserById(existingTrack.userTracked.userID.snowflake).tryAwait().orNull()?.username ?: "invalid-user"
                origin.ereply(Embeds.error("You may not un-track **$inputId** on **$siteName** unless you are the tracker ($tracker) or a channel moderator.")).awaitSingle()
            }
        }
    }
}
package moe.kabii.command.commands.trackers

import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.FeatureDisabledException
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.discord.trackers.AnimeTarget
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.discord.trackers.anime.MediaListDeletedException
import moe.kabii.discord.trackers.anime.MediaListIOException
import moe.kabii.discord.util.errorColor
import moe.kabii.discord.util.fbkColor
import moe.kabii.structure.extensions.propagateTransaction
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.tryAwait
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object MediaTrackerCommand : TrackerCommand {
    override suspend fun track(origin: DiscordParameters, target: TargetArguments, features: FeatureChannel?) {
        // if this is in a guild make sure the media list feature is enabled here
        if(origin.guild != null) {
            if(features == null || !features.animeChannel) throw FeatureDisabledException("anime", origin)
        }

        val site = requireNotNull(target.site as? AnimeTarget) { "Invalid target arguments provided to MediaTrackerCommand" }.dbSite
        val siteName = site.targetType.full
        val parser = site.parser
        val inputId = target.identifier

        // this may (ex. for kitsu) or may not (ex. for mal) make a call to find list ID - mal we only will know when we request the full list :/
        val siteListId = parser.getListID(inputId)
        if(siteListId == null) {
            origin.error("Unable to find **$siteName** list with identifier **$inputId**.").awaitSingle()
            return
        }

        // check if this list is already tracked in this channel, before we download the entire list (can be slow)
        val channelId = origin.chan.id.asLong()
        val existingTrack = newSuspendedTransaction {
            TrackedMediaLists.ListTarget.getExistingTarget(site, siteListId.toLowerCase(), channelId)
        }

        if(existingTrack != null) {
            origin.error("**$siteName/$inputId** is already tracked in this channel.").awaitSingle()
            return
        }

        val notice = origin.embed("Retrieving **$siteName** list...").awaitSingle()
        suspend fun editNotice(spec: (EmbedCreateSpec.() -> Unit)) {
            notice.edit { message -> message.setEmbed(spec) }.awaitSingle()
        }

        // download and validate list
        val mediaList = try {
            parser.parse(siteListId)
        } catch(delete: MediaListDeletedException) {
            null
        } catch(io: MediaListIOException) {
            LOG.warn("Media list IO issue: ${io.message}")

            editNotice {
                errorColor(this)
                setDescription("Unable to download your list from **$siteName**: ${io.message}")
            }
            return
        } catch(e: Exception) {
            LOG.warn("Caught Exception downloading media list: ${e.message}")
            LOG.trace(e.stackTraceString)

            editNotice {
                errorColor(this)
                setDescription("Unable to download your list! Possible $siteName outage.")
            }
            return
        }

        if(mediaList == null) {
            editNotice {
                errorColor(this)
                setDescription("Unable to find **$siteName** list with identifier **$inputId**.")
            }
            return
        }

        propagateTransaction {

            // track the list if it's not tracked at all, providing downloaded medialist as a base
            val dbList = TrackedMediaLists.MediaList.find {
                TrackedMediaLists.MediaLists.site eq site and
                        (TrackedMediaLists.MediaLists.siteChannelId eq siteListId)
            }.elementAtOrElse(0) { _ ->
                val listJson = mediaList.toDBJson()
                TrackedMediaLists.MediaList.new {
                    this.site = site
                    this.siteListId = siteListId
                    this.lastListJson = listJson
                }
            }

            // add this channel as a target for this list's updates, we know this does not exist
            TrackedMediaLists.ListTarget.new {
                this.mediaList = dbList
                this.discord = DiscordObjects.Channel.getOrInsert(channelId, origin.guild?.id?.asLong())
                this.userTracked = DiscordObjects.User.getOrInsert(origin.author.id.asLong())
            }
        }

        editNotice {
            fbkColor(this)
            setDescription("Now tracking **$inputId** on **$siteName**.")
        }
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetArguments) {
        val site = requireNotNull(target.site as? AnimeTarget) { "Invalid target arguments provided to MediaTrackerCommand" }.dbSite
        val siteName = site.targetType.full
        val parser = site.parser
        val inputId = target.identifier

        val siteListId = parser.getListID(inputId)
        if(siteListId == null) {
            origin.error("Unable to find $siteName list with identifier **${target.identifier}**.").awaitSingle()
            return
        }

        val channelId = origin.chan.id.asLong()

        propagateTransaction {
            val existingTrack = TrackedMediaLists.ListTarget.getExistingTarget(site, siteListId.toLowerCase(), channelId)
            if (existingTrack == null) {
                origin.error("**$inputId** is not currently being tracked on $siteName.").awaitSingle()
                return@propagateTransaction
            }

            if(origin.isPM // always allow untrack in pm
                    || origin.author.id.asLong() == existingTrack.userTracked.userID // not in pm, check for same user as tracker
                    || origin.event.member.get().hasPermissions(origin.guildChan, Permission.MANAGE_MESSAGES)) { // or channel moderator

                existingTrack.delete()
                origin.embed("No longer tracking **$inputId** on **$siteName**.").awaitSingle()

            } else {
                val tracker = origin.event.client.getUserById(existingTrack.userTracked.userID.snowflake).tryAwait().orNull()?.username ?: "invalid-user"
                origin.error("You may not un-track **$inputId** on **$siteName** unless you are the tracker ($tracker) or a channel moderator.").awaitSingle()
            }
        }
    }
}
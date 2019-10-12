package moe.kabii.discord.command.commands.trackers

import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MediaTarget
import moe.kabii.data.mongodb.TrackedMediaList
import moe.kabii.data.mongodb.TrackedMediaLists
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.command.FeatureDisabledException
import moe.kabii.discord.command.hasPermissions
import moe.kabii.discord.trackers.anime.MediaListEmpty
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock

object MediaTrackerCommand : Tracker<TargetMediaList> {
    override suspend fun track(origin: DiscordParameters, target: TargetMediaList) {
        // if this is in a guild make sure the media list feature is enabled here
        val config = origin.guild?.run { GuildConfigurations.getOrCreateGuild(id.asLong()) }
        if(config != null) {
            val features = config.options.featureChannels[origin.chan.id.asLong()]
            if(features == null || !features.twitchChannel) throw FeatureDisabledException("anime")
        }

        val targetName = target.list.id
        val lists = TrackedMediaLists.mediaLists
        val channelID = origin.chan.id.asLong()

        val parser = target.list.site.parser
        val listID = parser.getListID(target.list.id)
        if(listID == null) {
            origin.error("Unable to find ${target.list.site.full} list with identifier **$targetName**.").block()
            return
        }
        val targetList = target.list.copy(id = listID)
        // if the list is already tracked we need to return early rather than letting io be spammed. weird flow here but saving lots of i/o time. should still be refactored
        val existingTrack = lists.find { trackedList -> trackedList.list == targetList }?.targets?.find { target -> target.channelID == channelID }
        if(existingTrack != null) {
            origin.error("**${targetName}** is already tracked in this channel.").block()
            return
        }

        // validate list
        val request = parser.parse(listID)
        val mediaList = when(request) {
            is Ok -> request.value
            is Err -> {
                when(request.value) {
                    is MediaListEmpty -> {
                        origin.error("Unable to find ${targetList.site.full} list with identifier **${targetName}**.").block()
                        return
                    }
                    else -> {
                        origin.error("Error tracking list! Possible ${targetList.site.full} outage.").block()
                        return
                    }
                }
            }
        }
        // track the list if it's not tracked at all
        val find = lists.find { it.list == targetList }
        val trackedList = find ?:
                TrackedMediaList(list = targetList, savedMediaList = mediaList).also { list -> lists.add(list) }

        // add this channel if the list isn't already tracked in this channel
        // don't just compare MediaTarget because we don't care about WHO tracked the list in this case
        val mediaTarget = MediaTarget(channelID, origin.author.id.asLong())
        trackedList.targets.add(mediaTarget)
        trackedList.save()
        origin.embed("Now tracking **$targetName**!").block()
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetMediaList) {
        val lists = TrackedMediaLists.mediaLists
        val parser = target.list.site.parser
        val listID = parser.getListID(target.list.id)
        if(listID == null) {
            origin.error("Unable to find ${target.list.site.full} list with identifier **${target.list.id}**.").block()
            return
        }
        val targetName = target.list.id
        val targetList = target.list.copy(id = listID)

        // untrack the list from this location if it's tracked
        val trackedList = lists.find { it.list == targetList } // find tracked list with matching id/site

        if(trackedList != null) { // this list is tracked in some channel
            val trackedTarget = trackedList.targets.find { it.channelID == origin.chan.id.asLong()}
            if(trackedTarget != null) { // this list is tracked in this channel
                if(origin.isPM
                        || origin.author.id.asLong() == trackedTarget.discordUserID
                        || origin.event.member.get().hasPermissions(Permission.MANAGE_MESSAGES)) {
                    trackedList.targets.remove(trackedTarget)
                    trackedList.save()
                    origin.embed("No longer tracking **$targetName**.").block()
                    return
                } else {
                    val tracker = origin.chan.client.getUserById(trackedTarget.discordUserID.snowflake).tryBlock().orNull()?.username ?: "invalid-user"
                    origin.error("You may not untrack **$targetName** unless you are $tracker or a server moderator.").block()
                    return
                }
            }
        }
        origin.error("**$targetName** is not currently being tracked.").block()
    }
}
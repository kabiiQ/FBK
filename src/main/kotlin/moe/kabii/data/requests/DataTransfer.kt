package moe.kabii.data.requests

import discord4j.core.`object`.entity.Guild
import moe.kabii.LOG
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.registration.GuildCommandRegistrar
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.GuildTarget
import moe.kabii.data.relational.anime.TrackedMediaLists
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.discord.Reminders
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.YoutubeVideoTracks
import moe.kabii.data.relational.streams.youtube.ytchat.MembershipConfigurations
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeLiveChats
import moe.kabii.instances.FBK
import moe.kabii.util.extensions.propagateTransaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update

object DataTransfer {

    /**
     * Transfer ALL associated data for this guild onto a new instance of FBK.
     * Used for transferring users to the verified version of the bot, though can be used in any direction.
     * @param guild The guild requesting the transfer. ALL but ONLY data related to this guild should be transferred.
     * @param old The instance data is being transferred from. Reset to factory new after the transfer.
     * @param new The new instance that data should be placed onto.
     * @return A string with some formatted information what was transferred that can be displayed directly to the user.
     */
    suspend fun transferInstance(guild: Guild, old: FBK, new: FBK): String {

        val guildId = guild.id.asLong()
        val detail = StringBuilder()

        // MongoDB transfers
        // old should already exist
        val mongoOld = GuildConfigurations.guildConfigurations[GuildTarget(old.clientId, guildId)]
        checkNotNull(mongoOld)

        // full copy of configurations
        val fc = mongoOld.options.featureChannels.size
        if(fc > 0) {
            detail.appendLine("$fc channel-specific feature/tracker settings.")
        }
        val gc = mongoOld.guildCustomCommands.commands.size
        if(gc > 0) {
            detail.appendLine("$gc custom server commands.")
            try {
                GuildCommandRegistrar.updateGuildCommands(new, guild)
            } catch(e: Exception) {
                detail.appendLine("An error occured automatically syncing custom commands.")
            }
        }
        detail.appendLine("Any auto-role, music, translator, and welcomer settings.")
        val logs = mongoOld.logChannels().size
        if(logs > 0) {
            detail.appendLine("$logs enabled 'logging' channels.")
        }

        // replace mongo config with new copy
        val newTarget = GuildTarget(new.clientId, guildId)
        val mongoExisting = GuildConfigurations.guildConfigurations.remove(newTarget)
        checkNotNull(mongoExisting)
        val mongoNew = mongoOld.copy(_id = mongoExisting._id, guildClientId = new.clientId)
        GuildConfigurations.guildConfigurations[newTarget] = mongoNew
        mongoNew.save()
        mongoOld.removeSelf()

        // invalidate cached "tracked target" suggestions
        mongoNew.options.featureChannels.forEach { (channelId, _) ->
            TargetSuggestionGenerator.invalidateTargets(old.clientId, channelId)
            TargetSuggestionGenerator.invalidateTargets(new.clientId, channelId)
        }

        suspend fun tryUpdate(block: () -> Unit) {
            try {
                propagateTransaction {
                    block()
                }
            } catch(e: Exception) {
                LOG.error("Error in /transferdata: ${e.message}")
                detail.appendLine("Error transferring some tracker data, contact kabii to look into details.")
            }
        }

        // animelist targets
        tryUpdate {
            val mediaLists = TrackedMediaLists.ListTargets
                .innerJoin(DiscordObjects.Channels)
                .innerJoin(DiscordObjects.Guilds)
                .update({ // where
                    TrackedMediaLists.ListTargets.discordClient eq old.clientId and
                            (DiscordObjects.Guilds.guildID eq guildId)
                }) { update ->
                    update[TrackedMediaLists.ListTargets.discordClient] = new.clientId
                }
            if (mediaLists > 0) {
                detail.appendLine("$mediaLists tracked anime/manga lists.")
            }
        }

        // reminders
        tryUpdate {
            val reminders = Reminders
                .innerJoin(MessageHistory.Messages)
                .innerJoin(DiscordObjects.Channels)
                .innerJoin(DiscordObjects.Guilds)
                .update({ // where
                    Reminders.discordClient eq old.clientId and
                            (DiscordObjects.Guilds.guildID eq guildId)
                }) { update ->
                    update[Reminders.discordClient] = new.clientId
                }

            if (reminders > 0) {
                detail.appendLine("$reminders user reminders.")
            }
        }

        // yt membership configurations
        tryUpdate {
            val memberSetup = MembershipConfigurations
                .innerJoin(DiscordObjects.Guilds)
                .update({
                    MembershipConfigurations.discordClient eq old.clientId and
                            (DiscordObjects.Guilds.guildID eq guildId)
                }) { update ->
                    update[MembershipConfigurations.discordClient] = new.clientId
                }
            if (memberSetup > 0) {
                detail.appendLine("$memberSetup YouTube Membership link.")
            }
        }

        // yt live chat tracks
        tryUpdate {
            val ytLiveChat = YoutubeLiveChats
                .innerJoin(DiscordObjects.Channels)
                .innerJoin(DiscordObjects.Guilds)
                .update({
                    YoutubeLiveChats.discordClient eq old.clientId and
                            (DiscordObjects.Guilds.guildID eq guildId)
                }) { update ->
                    update[YoutubeLiveChats.discordClient] = new.clientId
                }
            if (ytLiveChat > 0) {
                detail.appendLine("$ytLiveChat tracked YouTube live chat relays")
            }
        }

        // yt video tracks
        tryUpdate {
            val ytVideoTrack = YoutubeVideoTracks
                .innerJoin(DiscordObjects.Channels)
                .innerJoin(DiscordObjects.Guilds)
                .update({
                    YoutubeVideoTracks.discordClient eq old.clientId and
                            (DiscordObjects.Guilds.guildID eq guildId)
                }) { update ->
                    update[YoutubeVideoTracks.discordClient] = new.clientId
                }
            if (ytVideoTrack > 0) {
                detail.appendLine("$ytVideoTrack individually tracked YouTube videos")
            }
        }

        // tracked stream channels
        tryUpdate {
            val streamTargets = TrackedStreams.Targets
                .innerJoin(DiscordObjects.Channels)
                .innerJoin(DiscordObjects.Guilds)
                .update({
                    TrackedStreams.Targets.discordClient eq old.clientId and
                            (DiscordObjects.Guilds.guildID eq guildId)
                }) { update ->
                    update[TrackedStreams.Targets.discordClient] = new.clientId
                }
            if (streamTargets > 0) {
                detail.appendLine("$streamTargets tracked live stream channels.")
            }
        }

        // tracked social feeds
        tryUpdate {
            val twitterFeeds = TrackedSocialFeeds.SocialTargets
                .innerJoin(DiscordObjects.Channels)
                .innerJoin(DiscordObjects.Guilds)
                .update({
                    TrackedSocialFeeds.SocialTargets.client eq old.clientId and
                            (DiscordObjects.Guilds.guildID eq guildId)
                }) { update ->
                    update[TrackedSocialFeeds.SocialTargets.client] = new.clientId
                }
            if (twitterFeeds > 0) {
                detail.appendLine("$twitterFeeds tracked Twitter feeds.")
            }
        }

        return detail.toString()
    }
}
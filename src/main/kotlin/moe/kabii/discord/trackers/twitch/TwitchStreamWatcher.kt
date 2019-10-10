package moe.kabii.discord.trackers.twitch

import discord4j.core.DiscordClient
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.TextChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.runBlocking
import moe.kabii.data.mongodb.*
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.helix.*
import moe.kabii.rusty.*
import moe.kabii.structure.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.time.Duration
import java.time.Instant

class TwitchStreamWatcher(val discord: DiscordClient) : Thread("StreamWatcher") {
    var active = true

    override fun run() {
        while(active) {
            val start = Instant.now()
            transaction {
                val tracked = TrackedStreams.Stream.all() // capture the currently tracked streams

                // call api and generate <twitchstreamconfig, twitchstreams
                val runDuration = Duration.between(start, Instant.now())
                val streamIDs = tracked.map(TrackedStreams.Stream::stream)
                val users = TwitchHelix.getUsers(streamIDs) // bulk call re-associated later for the sake of important api call optimization
                val streams = TwitchHelix.getStreams(streamIDs)
                tracked.forEach { stream ->
                    val id = stream.stream
                    println(id)
                    Try { updateStream(stream, streams.getValue(id), users.getValue(id)) }.result.ifErr { err -> err.printStackTrace() } // don't let this thread die
                }

                if(runDuration.toMinutes() < 2) {
                    val wait = 120000L - runDuration.toMillis()
                    sleep(wait)
                }
            }
        }
    }

    @WithinExposedContext
    private fun updateStream(config: TrackedStreams.Stream, apiStream: HelixStream, apiUser: HelixUser) {
        val stream = when(apiStream) {
            is Ok -> apiStream.value
            is Err -> {
                if(apiStream.value is HelixIOErr) return // actual error contacting Twitch
                else null
            }
        }
        val user = apiUser.orNull()
        if(user == null) {
            // todo untrack system
            println("Invalid Twitch user: ${config.stream}")
            return
        }

        //val notifications = config.targets.flatMap(TrackedStreams.Target::notifications) // get active stream notification messages
        val targets = config.targets
        targets.forEach {
            println(it)
        }
        if(stream == null) { // stream is not live, if there are any existing notifications, edit/remove them, otherwise ignore
            targets.flatMap(TrackedStreams.Target::notifications).forEach { activeNotification -> // though there will only be one notification per target this list still needs to be flattened
                val embed = TwitchEmbedBuilder(user).statistics(activeNotification)
                activeNotification.delete()
                val channelID = activeNotification.target.channel
                val discordMessage = discord.getMessageById(channelID.snowflake, activeNotification.message.snowflake).tryBlock().orNull() ?: return@forEach
                val guild = discordMessage.guild.tryBlock()
                val summarize = guild.flatMap({ guild ->
                    val guildConfig = GuildConfigurations.getOrCreateGuild(guild.id.asLong())
                    guildConfig.getOrCreateFeatures(channelID).featureSettings.streamSummaries
                }, mapperErr = { true }) // pm
                if(summarize) {
                    discordMessage.edit { spec -> spec.setEmbed(embed.create) }
                } else {
                    discordMessage.delete()
                }.subscribe()
            }
            return
        }
        // stream is live, edit or post a notification in each target channel
        targets.forEach { target ->
            val chan = when(val channel = discord.getChannelById(target.channel.snowflake).ofType(MessageChannel::class.java).tryBlock()) {
                is Ok -> channel.value
                is Err -> {
                    val err = channel.value
                    if(err is ClientException && err.status.code() == 404) { // channel deleted
                        target.delete()
                    } // else network error will retry next tick
                    return@forEach
                }
            }
            val guildChannel = chan as? TextChannel
            val guildConfig = guildChannel?.run { GuildConfigurations.getOrCreateGuild(guildId.asLong()) }
            val thumbnail = guildConfig?.options
                ?.featureChannels?.get(chan.id.asLong())
                ?.featureSettings?.streamThumbnails ?: true
            val embed = TwitchEmbedBuilder(user).stream(stream, thumbnail)

            val existingNotif = target.notifications.firstOrNull()
            if(existingNotif == null) { // post a new stream notification

                // get mention role if exists
                val mentionRole = guildConfig?.twitchMentionRoles?.get(config.stream)
                    ?.run {
                        guildChannel.guild
                            .flatMap { guild -> guild.getRoleById(this.snowflake) }.tryBlock().orNull()
                        // todo can remove missing roles here
                    }
                val mention = mentionRole?.run {
                    edit { role -> role.setMentionable(true) }
                        .map { role -> role.mention }
                        .tryBlock().orNull()
                }
                val message = chan.createMessage { spec ->
                    if(mention != null) spec.setContent(mention)
                    spec.setEmbed(embed.automatic)
                }.block()

                mentionRole?.run { edit { role -> role.setMentionable(false)}.subscribe() }
                transaction {
                    TrackedStreams.Notification.new {
                        this.message = message.id.asLong()
                        this.target = target
                        this.startTime = stream.startedAt.jodaDateTime
                        this.peakViewers = stream.viewer_count
                        this.ticks = 1
                        this.averageViewers = stream.viewer_count
                    }
                }
             } else { // edit stream notification, reposting as needed
                val notificationMessage = discord.getMessageById(target.channel.snowflake, existingNotif.message.snowflake).tryBlock().orNull()
                existingNotif.updateViewers(stream.viewer_count)
                if(notificationMessage != null) {
                    notificationMessage.edit { spec -> spec.setEmbed(embed.automatic) }.block()
                } else {
                    transaction {
                        val newMessage = chan.createMessage { spec -> spec.setEmbed(embed.automatic) }.block()
                        existingNotif.message = newMessage.id.asLong()
                    }
                }
            }
        }
    }
}
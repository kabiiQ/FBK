package moe.kabii.discord.tasks

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.PrivateChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.relational.discord.Reminder
import moe.kabii.data.relational.discord.Reminders
import moe.kabii.discord.util.reminderColor
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.WithinExposedContext
import moe.kabii.structure.extensions.*
import moe.kabii.util.DurationFormatter
import moe.kabii.util.EmojiCharacters
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.joda.time.DateTime
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class ReminderWatcher(val discord: GatewayDiscordClient) : Runnable {
    private val updateInterval = 60_000L

    override fun run() {
        loop {
            // grab reminders ending in next 2 minutes
            val start = Instant.now()
            newSuspendedTransaction {
                try {
                    val window = DateTime.now().plus(updateInterval)
                    val reminders = Reminder.find { Reminders.remind lessEq window }.toList()

                    // launch coroutine for precise reminder notifications. run on reminder dispatcher threads
                    val job = SupervisorJob()
                    val discordScope = CoroutineScope(DiscordTaskPool.reminderThreads + job)

                    reminders.map { reminder ->
                        discordScope.launch {
                            newSuspendedTransaction {
                                scheduleReminder(reminder)
                            }
                        }
                    }.joinAll() // wait for all reminders to finish to make sure these are removed before next set
                } catch(e: Exception) {
                    LOG.error("Uncaught exception in ReminderWatcher :: ${e.message}")
                    LOG.debug(e.stackTraceString)
                } // don't let this thread die
            }
            val runtime = Duration.between(start, Instant.now())
            val delay = updateInterval - runtime.toMillis()
            delay(max(delay, 0L)) // don't sleep negative - not sure how this was happening though
        }
    }

    @WithinExposedContext
    private suspend fun scheduleReminder(reminder: Reminder) {
        val time = Duration.between(Instant.now(), reminder.remind.javaInstant)
        delay(max(time.toMillis(), 0L))
        val user = discord.getUserById(reminder.user.userID.snowflake)
            .tryAwait().orNull()
        if(user == null) {
            LOG.warn("Skipping reminder: user ${reminder.user} not found") // this should not happen
            return
        }

        // try to send reminder, send in PM if failed
        val age = Duration.between(reminder.created.javaInstant, Instant.now())
        val createdTime = DurationFormatter(age).fullTime
        val embed: EmbedBlock = {
            reminderColor(this)
            val clock = EmojiCharacters.alarm
            setAuthor("$clock Reminder for ${user.username}#${user.discriminator} $clock", null, user.avatarUrl)
            val created = "Reminder created $createdTime ago."
            val desc = if(reminder.originMessage != null) {
                "[$created](${reminder.originMessage!!.jumpLink})"
            } else created
            setDescription(desc)
            val content = reminder.content
            if(content.isNotBlank()) {
                addField("Reminder:", content, false)
            }
            setFooter("Reminder created", null)
            setTimestamp(reminder.created.javaInstant)
        }

        suspend fun sendReminder(target: MessageChannel) {
            target.createMessage { spec ->
                spec.setContent(user.mention)
                spec.setEmbed(embed)
            }.awaitSingle()
        }

        suspend fun dmFallback() {
            val dmChannel = user.privateChannel.tryAwait().orNull()
            if(dmChannel != null) {
                sendReminder(dmChannel)
            } else {
                LOG.info("Unable to send reminder: unable to send DM fallback message :: $reminder")
            }
        }
        // if guild channel, try to send and fall back to DM
        // if DM channel, send to DM
        val discordChannel = discord.getChannelById(reminder.channel.snowflake)
            .ofType(MessageChannel::class.java)
            .tryAwait().orNull()
        try {
            when (discordChannel) {
                is PrivateChannel -> sendReminder(discordChannel)
                is GuildMessageChannel -> {
                    val member = user.asMember(discordChannel.guildId).tryAwait().orNull()
                    if (member != null) {
                        try {
                            sendReminder(discordChannel)
                        } catch (ce: ClientException) {
                            val err = ce.status.code()
                            if (err == 403 || err == 404) {
                                // unable to send message, try to DM fallback
                                dmFallback()
                            }
                        }

                    } else {
                        // member no longer in server, try to DM fallback
                        dmFallback()
                    }
                }
            }
        } catch(ce: ClientException) {
            LOG.info("Completely unable to send reminder: skipping :: $reminder")
        } catch(e: Exception) {
            LOG.error("Uncaught exception sending reminder :: ${e.message}")
            LOG.debug(e.stackTraceString)
        }
        reminder.delete()
    }

}
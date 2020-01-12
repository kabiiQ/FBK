package moe.kabii.discord.tasks

import discord4j.core.DiscordClient
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.PrivateChannel
import discord4j.core.`object`.entity.TextChannel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.relational.Reminder
import moe.kabii.data.relational.Reminders
import moe.kabii.discord.command.reminderColor
import moe.kabii.rusty.Try
import moe.kabii.structure.*
import moe.kabii.util.DurationFormatter
import moe.kabii.util.EmojiCharacters
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import reactor.core.publisher.toFlux
import java.time.Duration
import java.time.Instant

class ReminderWatcher(val discord: DiscordClient) : Thread("Reminders") {
    val active = true
    private val updateInterval = 60_000L

    override fun run() {
        while(active) {
            // grab reminders ending in next 2 minutes
            val start = Instant.now()
            transaction {
                Try {
                    val window = DateTime.now().plus(updateInterval)
                    val reminders = Reminder.find { Reminders.remind lessEq window }.toList()

                    // launch coroutine for precise reminder notifications. run on current "Reminders" thread
                    runBlocking {
                        reminders.map { reminder ->
                            launch {
                                scheduleReminder(reminder)
                            }
                        }.joinAll() // wait for all reminders to finish to make sure these are removed before next set
                    }
                }.result.ifErr { t ->
                    LOG.error("Uncaught exception in ReminderWatcher: ${t.message}")
                    LOG.warn(t.stackTraceString)
                } // don't let this thread die
            }
            val runtime = Duration.between(start, Instant.now())
            sleep(updateInterval - runtime.toMillis())
        }
    }

    @WithinExposedContext
    private suspend fun scheduleReminder(reminder: Reminder) {
        // todo check if was old reminder
        val time = Duration.between(Instant.now(), reminder.remind.javaInstant)
        delay(time)
        val user = discord.getUserById(reminder.user.userID.snowflake)
            .tryBlock().orNull()
        if(user == null) {
            LOG.warn("Skipping reminder: user ${reminder.user} not found") // this should not happen
            return
        }
        // get guild channel/pm channel
        val discordChannel = discord.getChannelById(reminder.channel.snowflake)
            .ofType(MessageChannel::class.java)
            .tryBlock().orNull()
        val remindChannel: MessageChannel? = when(discordChannel) {
            is PrivateChannel -> discordChannel
            is TextChannel -> {
                val member = user.asMember(discordChannel.guildId).tryBlock().orNull()
                if(member != null) discordChannel
                else user.privateChannel.tryBlock().orNull() // if user is no longer in guild, send reminder in pm
            }
            else -> null // guild channel can be deleted entirely
        }
        if(remindChannel == null) {
            LOG.info("Skipping reminder: channel ${reminder.channel} is no longer valid")
            return
        }
        val age = Duration.between(reminder.created.javaInstant, Instant.now())
        val createdTime = DurationFormatter(age).fullTime
        val embed: EmbedReceiver = {
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
        remindChannel.createMessage { spec ->
            spec.setContent(user.mention)
            spec.setEmbed(embed)
            }.block()
        reminder.delete()
    }
}
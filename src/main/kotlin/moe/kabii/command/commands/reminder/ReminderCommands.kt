package moe.kabii.command.commands.reminder

import discord4j.common.util.TimestampFormat
import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.discord.Reminder
import moe.kabii.data.relational.discord.Reminders
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.MessageColors
import moe.kabii.util.DurationFormatter
import moe.kabii.util.DurationParser
import moe.kabii.util.extensions.tryAwait
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

object ReminderCommands : CommandContainer {
    object RemindMe : Command("remind") {
        override val wikiPath = "Utility-Commands#commands"

        init {
            discord {
                // create a reminder for the current user - if pm or has pm flag, send message in pm instead
                // remindme time message !dm
                val timeArg = args.string("when")
                val time = DurationParser.tryParse(timeArg, startAt = ChronoUnit.MINUTES)

                if(time == null) {
                    ereply(Embeds.error("**$timeArg** is an invalid reminder delay. Examples: 10m, 6h, 1d, 1d4h, 1w")).awaitSingle()
                    return@discord
                }

                val length = DurationFormatter(time).fullTime
                if(time.seconds < 60 || time.toDays() > 732) {
                    // currently this is technically a limitation because we only pull from the database once per minute and shorter reminders will be lost as a result.
                    // there would be easy to work around BUT I felt reminders < 1 minute are probably in error or at best a joke anyways
                    // also 2 years limit just for some arbitrary practicality
                    ereply(Embeds.error("**$timeArg** interpreted as **$length**. Please specify a reminder time between 1 minute and 2 years.")).awaitSingle()
                    return@discord
                }

                val replyPrivate = this.isPM || args.optBool("dm") == true
                val replyChannel = if(replyPrivate) {
                    val dmChan = author.privateChannel.tryAwait().orNull()
                    if(dmChan == null) {
                        if(!isPM) { // small optimization since we can't reply anyways :)
                            ereply(Embeds.error("I am unable to DM you at this time. Please check your privacy settings.")).awaitSingle()
                        }
                        return@discord
                    } else dmChan
                } else chan

                // add the reminder to the database
                val reminder = transaction {
                    Reminder.new {
                        user = DiscordObjects.User.getOrInsert(author.id.asLong())
                        channel = replyChannel.id.asLong()
                        created = DateTime.now()
                        remind = DateTime.now().plusSeconds(time.seconds.toInt())
                        content = args.optStr("what") ?: ""
                        originMessage = MessageHistory.Message.getOrInsert(interaction.message.get())
                    }
                }
                val location = if(replyPrivate) "private message" else "reminder in this channel"
                val reminderID = reminder.id
                val reminderTarget = TimestampFormat.SHORT_DATE_TIME.format(Instant.now().plus(time))

                val reply = Embeds.other("Reminder created for $reminderTarget.\nYou will be sent a $location in **$length**.", MessageColors.reminder)
                    .withFooter(EmbedCreateFields.Footer.of("Reminder ID: $reminderID", null))
                val action = if(replyPrivate) ereply(reply) else ireply(reply)
                action.awaitSingle()
            }
        }
    }

    object CancelReminder : Command("cancel") {
        override val wikiPath = "Utility-Commands#commands"

        init {
            discord {
                // cancel <reminder id>
                val idArg = args.int("ReminderID")
                val reminder = transaction {
                    Reminder
                        .find { Reminders.id eq idArg }
                        .firstOrNull { rem -> rem.user.userID == this@discord.author.id.asLong() }
                        ?.also(Reminder::delete)
                }
                if(reminder == null) ereply(Embeds.error("You did not create the reminder with ID #**$idArg**.")).awaitSingle()
                else ereply(Embeds.fbk("Your reminder with ID #**$idArg** has been cancelled.")).awaitSingle()
            }
        }
    }
}
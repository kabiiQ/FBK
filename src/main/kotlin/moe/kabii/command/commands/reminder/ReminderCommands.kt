package moe.kabii.command.commands.reminder

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.data.relational.DiscordObjects
import moe.kabii.data.relational.MessageHistory
import moe.kabii.data.relational.Reminder
import moe.kabii.data.relational.Reminders
import moe.kabii.discord.util.reminderColor
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.extensions.tryAwait
import moe.kabii.util.DurationFormatter
import moe.kabii.util.DurationParser
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.time.Duration

object ReminderCommands : CommandContainer {
    object RemindMe : Command("remind", "reminder", "setreminder", "remindme") {
        override val wikiPath = "Utility-Commands#commands"

        init {
            discord {
                // create a reminder for the current user - if pm or has pm flag, send message in pm instead
                // remindme time message !dm
                if(args.isEmpty()) {
                    usage("**remind** schedules the bot to send you a reminder in the future. If ran in DM or the reminder contains the flag !dm, the reminder will be sent via DM. Otherwise, you will be pinged in the current channel.",
                        "remind <time until reminder, examples: 1m, 1:00, 2h30m, 3d, 2 days 3 hours, 2d3h> (reminder message)").awaitSingle()
                    return@discord
                }
                var time: Duration? = null
                var argIndex = 0
                val timeArg = StringBuilder()
                for((index, arg) in args.withIndex()) {
                    timeArg.append(arg)
                    val parse = DurationParser.tryParse(timeArg.toString())
                    if(parse == null) break
                    else {
                        time = parse
                        argIndex = index
                    }
                }

                if(time == null) {
                    usage("**${args[0]}** is an invalid reminder delay.", "remindme <time until reminder> <reminder message>").awaitSingle()
                    return@discord
                }
                val length = DurationFormatter(time).fullTime
                if(time.seconds < 60 || time.toDays() > 732) {
                    // currently this is technically a limitation because we only pull from the database once per minute and shorter reminders will be lost as a result.
                    // there would be easy to work around BUT I felt reminders < 1 minute are probably in error or at best a joke anyways, if someone tries to write "20"
                    // and this would be taken as 20 seconds rather than if they expected minutes or hours
                    // also 2 years limit just for some arbitrary practicality
                    error("**${args[0]}** was taken to mean **$length**. Please specify a reminder time of at least 1 minute.").awaitSingle()
                    return@discord
                }

                var replyPrivate = this.isPM
                // remove flags from message
                val reminderContent = args.drop(argIndex + 1).filter { arg ->
                    when(arg.toLowerCase()) {
                        "!dm", "!pm" -> {
                            replyPrivate = true
                            false
                        }
                        else -> true
                    }
                }
                    .joinToString(" ")
                    .run {
                        if(guild != null) replace(guild.id.asString(), "") else this
                    }
                val replyChannel = if(replyPrivate) {
                    val dmChan = author.privateChannel.tryAwait().orNull()
                    if(dmChan == null) {
                        if(!isPM) { // small optimization since we can't reply anyways :)
                            error("I am unable to DM you at this time. Please check your privacy settings.").awaitSingle()
                            return@discord
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
                        content = reminderContent
                        originMessage = MessageHistory.Message.find { MessageHistory.Messages.messageID eq event.message.id.asLong() }
                            .elementAtOrElse(0) { _ ->
                                MessageHistory.Message.new(guild?.id?.asLong(), event.message)
                            }
                    }
                }
                val location = if(replyPrivate) "private message" else "reminder in this channel"
                val reminderID = reminder.id
                val embed: EmbedBlock = {
                    reminderColor(this)
                    setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                    setDescription("Reminder created! You will be sent a $location in **$length**.")
                    setFooter("Reminder ID: $reminderID", null)
                }
                chan.createEmbed(embed).awaitSingle()
            }
        }
    }

    object CancelReminder : Command("cancelreminder", "remindercancel","cancel") {
        override val wikiPath = "Utility-Commands#commands"

        init {
            discord {
                // cancel <reminder id>
                val targetReminder = args.getOrNull(0)?.toLongOrNull()
                if(targetReminder == null) {
                    usage("**cancel** is used to cancel an active reminder early.", "cancel <reminder ID>").awaitSingle()
                    return@discord
                }
                val reminder = transaction {
                    Reminder
                        .find { Reminders.id eq targetReminder }
                        .firstOrNull { rem -> rem.user.userID == this@discord.author.id.asLong() }
                        ?.also(Reminder::delete)
                }
                if(reminder == null) {
                    error("You did not create the reminder with ID #**$targetReminder**.").awaitSingle()
                    return@discord
                }
                embed("Your reminder with ID #**$targetReminder** has been cancelled.").awaitSingle()
            }
        }
    }
}
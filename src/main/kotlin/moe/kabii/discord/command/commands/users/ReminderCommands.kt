package moe.kabii.discord.command.commands.users

import moe.kabii.data.relational.DiscordObjects
import moe.kabii.data.relational.MessageHistory
import moe.kabii.data.relational.Reminder
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.reminderColor
import moe.kabii.structure.EmbedReceiver
import moe.kabii.structure.tryBlock
import moe.kabii.util.DurationFormatter
import moe.kabii.util.DurationParser
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

object ReminderCommands : CommandContainer {
    object RemindMe : Command("remindme", "reminder", "setreminder", "remind") {
        init {
            discord {
                // create a reminder for the current user - if pm or has pm flag, send message in pm instead
                // remindme time message !dm
                if(args.size < 2) {
                    usage("**remindme** schedules the bot to send you a reminder in the future. If ran in DM or the reminder contains the flag !dm, the reminder will be sent via DM. Otherwise, you will be pinged in the current channel.",
                        "remindme <time until reminder, ex: 1:30 / 1m30s, 1:0:0:0 / 1d, or 1:20:0 / 1h20m> <reminder message>").block()
                    return@discord
                }
                val time = DurationParser.tryParse(args[0])
                if(time == null) {
                    usage("**${args[0]}** is an invalid reminder delay.", "remindme <time until reminder> <reminder message>").block()
                    return@discord
                }
                val length = DurationFormatter(time).fullTime
                if(time.seconds < 60 || time.toDays() > 732) {
                    // currently this is technically a limitation because we only pull from the database once per minute and shorter reminders will be lost as a result.
                    // there are easy changes for the design here BUT I felt reminders < 1 minute are probably in error anyways if someone tries to write "20"
                    // and this would be taken as 20 seconds rather than if they expected minutes or hours
                    // also 2 years limit just for some arbitrary practicality
                    error("**${args[0]}** was taken to mean **$length**. Please specify a reminder time of at least 1 minute.").block()
                    return@discord
                }

                var replyPrivate = this.isPM
                // remove flags from message
                val reminderContent = args.drop(1).filter { arg ->
                    when(arg.toLowerCase()) {
                        "!dm", "!pm" -> {
                            replyPrivate = true
                            false
                        }
                        else -> true
                    }
                }
                    .joinToString("")
                    .replace("@everyone", "")
                    .run {
                        if(guild != null) replace(guild.id.asString(), "") else this
                    }
                val replyChannel = if(replyPrivate) {
                    val dmChan = author.privateChannel.tryBlock().orNull()
                    if(dmChan == null) {
                        if(!isPM) { // small optimization since we can't reply anyways :)
                            error("I am unable to DM you at this time. Please check your privacy settings.").block()
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
                        originMessage = if(guild != null) {
                            // if guild does not have message history enabled. we are obviously storing the content for the reminder anyways so this is an exception
                            MessageHistory.Message.find { MessageHistory.Messages.messageID eq event.message.id.asLong() }
                                .elementAtOrElse(0) { _ ->
                                    MessageHistory.Message.new(guild.id.asLong(), event.message)
                                }
                        } else null
                    }
                }
                val location = if(replyPrivate) "private message" else "reminder in this channel"
                val reminderID = reminder.id
                val embed: EmbedReceiver = {
                    reminderColor(this)
                    setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                    setDescription("Reminder created! You will be sent a $location in **$length**.")
                    setFooter("Reminder ID: $reminderID", null)
                }
                chan.createEmbed(embed).block()
            }
        }
    }
}
package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.data.relational.discord.Reminder
import moe.kabii.data.relational.discord.Reminders
import moe.kabii.data.relational.streams.youtube.YoutubeScheduledEvent
import moe.kabii.data.relational.streams.youtube.YoutubeScheduledEvents
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.audio.GuildAudio
import moe.kabii.util.extensions.javaInstant
import moe.kabii.util.extensions.propagateTransaction
import org.joda.time.DateTime
import org.joda.time.Duration
import java.time.Instant

/**
 * Does checks for upcoming tasks for shutdown safety
 */
object Shutdown : Command("shutdown") {
    override val wikiPath: String? = null

    init {
        terminal {
            println("Checking upcoming tasks for shutdown...")

            // Active music bots
            val activeAudio = AudioManager.guilds.values.count(GuildAudio::playing)
            if(activeAudio > 0) {
                println("$activeAudio guilds have audio playing.")
            }

            val now = DateTime.now()
            val window = now.plus(Duration.standardMinutes(5))
            // Estimate upcoming reminders
            propagateTransaction {
                val reminders = Reminder.find { Reminders.remind lessEq window }
                val count = reminders.count()
                if(count > 0) {
                    val info = reminders.joinToString { reminder ->
                        val time = java.time.Duration.between(Instant.now(), reminder.remind.javaInstant)
                        "${time.seconds} seconds"
                    }
                    println("$count reminders upcoming: $info")
                }
            }

            // Estimate upcoming notifs
            propagateTransaction {
                val (upcoming, overdue) = YoutubeScheduledEvent.find {
                        YoutubeScheduledEvents.scheduledStart lessEq window
                }.partition { upcoming ->
                    upcoming.scheduledStart >= now
                }
                println("${upcoming.size} videos upcoming, ${overdue.size} overdue")
            }
        }
    }
}
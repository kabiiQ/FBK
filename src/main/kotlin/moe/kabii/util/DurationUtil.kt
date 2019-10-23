package moe.kabii.util

import moe.kabii.rusty.Try
import java.time.Duration
import java.time.temporal.ChronoUnit

object DurationParser {
    private val categories = arrayOf(ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS)

    fun tryParse(input: String): Duration? {
        // try to parse as days:minutes:hours:seconds, "20" will work take as 20 seconds
        val args = input.split(":")
        val components = sequence {
            categories.mapIndexed { index, chronoUnit ->
                // work backwards through arguments provided to start with our lowest supported unit of seconds
                val element = args.getOrNull(args.size - index - 1)?.toLongOrNull() ?: return@sequence
                yield(Duration.of(element, chronoUnit))
            }
        }.toList()
        if(components.isNotEmpty()) return components.reduce(Duration::plus)
        // if no components specified in first format, try to parse as "20 seconds", "20s"
        return input.trim()
            .toUpperCase()
            .replace(Regex("DAYS?"), "D")
            .replace(Regex("H(OU)?RS?"), "H")
            .replace(Regex("MIN(UTE)?S?"), "M")
            .replace(Regex("SEC(OND?)?S?"), "S")
            .let { str -> // put into proper Duration format with T after days component
                val index = str.indexOf("D")
                if(index != -1) {
                    if(index + 1 >= str.length) { // days is only component
                        str
                    } else {
                        str.replaceFirst("D", "DT")
                    }
                } else "T$str"
            }
            .replace(Regex("\\s"), "")
            .let("P"::plus)
            .run { Try { Duration.parse(this) }}
            .result.orNull()
    }

    data class UnknownLengthParse(val parsedDuration: Duration, val remainder: Collection<String>)

    fun tryUnknownLengthParse(args: Collection<String>): UnknownLengthParse? = TODO()
}

class DurationFormatter(val duration: Duration) {
    constructor(millisDuration: Long) : this(Duration.ofMillis(millisDuration))

    private val days = duration.seconds / 86400
    private val hours = duration.seconds / 3600
    private val minutes = duration.seconds / 60
    private val hoursPart = hours - (days * 24)
    private val minutesPart = minutes - (hours * 60)
    private val secondsPart = duration.seconds - (minutes * 60)

    val colonTime: String
        get() {
            fun leading(value: Long) = String.format("%02d", value)
            val output = StringBuilder()
            if(hours > 0L) { // always have at least min:sec
                output.append(hours)
                    .append(":")
                    .append(leading(minutesPart))
            } else output.append(minutesPart)
            output.append(":")
                .append(leading(secondsPart))
            return output.toString()
        }

    val fullTime: String
        get() {
            fun plural(value: Long) = if(value != 1L) "s" else ""
            val output = StringBuilder()
            if(days > 0L) {
                output.append(days)
                    .append(" day")
                    .append(plural(days))
                    .append(", ")
            }
            if(hours > 0L) {
                output.append(hoursPart)
                    .append(" hour")
                    .append(plural(hoursPart))
                    .append(", ")
            }
            // always include either minutes or seconds
            if(minutes > 0L) {
                output.append(minutesPart)
                    .append(" minute")
                    .append(plural(minutesPart))
            } else {
                output.append(secondsPart)
                    .append(" second")
                    .append(plural(secondsPart))
            }
            return output.toString()
        }
}
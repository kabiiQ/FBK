package moe.kabii.util

import moe.kabii.rusty.Try
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object DurationParser {
    private val patternYears = Regex("([0-9]{1})Y(?:YEARS?)?") // match 2Y, 2YEAR, 2YEARS
    private val patternMonths = Regex("([0-9]+)MONTHS?")

    private val categories = arrayOf(ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS, ChronoUnit.YEARS)

    fun tryParse(input: String, startAt: ChronoUnit? = null, stopAt: ChronoUnit? = null): Duration? {
        // try to parse as weeks:days:minutes:hours:seconds, rtl so "20" will work take as 20 seconds
        val args = input.split(":")
        val components = sequence {
            val iStart = startAt?.run(categories::indexOf) ?: 0
            val iStop = stopAt?.run(categories::indexOf) ?: categories.size - 1
            val parts = categories.toList().subList(iStart, iStop)

            parts.mapIndexed { index, chronoUnit ->
                // work backwards through arguments provided to start with our lowest supported unit of seconds
                val element = args.getOrNull(args.size - index - 1)?.toLongOrNull() ?: return@sequence
                yield(Duration.of(element, chronoUnit))
            }
        }.toList()
        if(components.isNotEmpty()) return components.reduce(Duration::plus)
        // if no components specified in first format, try to parse as "20 seconds", "20s"
        return input.trim()
            .toUpperCase()
            .replace(" ", "") // remove spaces
            .let { string -> // parse weeks and days
                var str = string

                var days = 0L
                // get days and weeks component from input - weeks+ is not part of Duration, so we need to add to the days component
                val inputYears = patternYears.find(str)?.run {
                    str = str.replace(this.value, "")
                    groups[1]?.value?.toLong()
                } ?: 0
                if(inputYears > 0) {
                    val future = LocalDateTime.now().plusYears(inputYears)
                    days += Duration.between(LocalDateTime.now(), future).toDays()
                }

                val inputMonths = patternMonths.find(str)?.run {
                    str = str.replace(this.value, "")
                    groups[1]?.value?.toLong()
                } ?: 0L
                if(inputMonths > 0) {
                    val future = LocalDateTime.now().plusMonths(inputMonths)
                    days += Duration.between(LocalDateTime.now(), future).toDays()
                }

                val patternWeeks = Regex("([0-9]+)W(?:EEKS?)?") // match 2W, 2WEEK, 2WEEKS
                val matchWeeks = patternWeeks.find(str)
                val inputWeeks = matchWeeks?.run {
                    str = str.replace(this.value, "")
                    groups[1]?.value?.toLong()
                } ?: 0L
                days += inputWeeks * 7

                val patternDays = Regex("([0-9]+)D(?:AYS?)?")
                val matchDays = patternDays.find(str)
                val inputDays = matchDays?.run {
                    str = str.replace(this.value, "")
                    groups[1]?.value?.toLong()
                } ?: 0L
                days += inputDays

                if(days > 0) {
                    "${days}D$str"
                } else str
            }
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
}

class DurationFormatter(val duration: Duration) {
    constructor(millisDuration: Long) : this(Duration.ofMillis(millisDuration))

    private val days = duration.seconds / 86400
    private val hours = duration.seconds / 3600
    private val minutes = duration.seconds / 60
    private val hoursPart = hours - (days * 24)
    private val minutesPart = minutes - (hours * 60)
    private val secondsPart = duration.seconds - (minutes * 60)

    private fun leading(value: Long) = String.format("%02d", value)

    val colonTime: String
    get() {
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

    val asUptime: String // just hours:minutes
    get() = "$hours:${leading(minutesPart)}"

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
        // always include either minutes or seconds (even if minutesPart = 0. this is a style choice)
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

    val inputTime: String
    get() {
        // output in 'input' style - i.e. 1w2d3h
        val output = StringBuilder()
        if(days > 0L) {
            output.append(days)
                .append("d")
        }
        if(hoursPart > 0L) {
            output.append(hoursPart)
                .append("h")
        }
        if(minutesPart > 0L) {
            output.append(minutesPart)
                .append("m")
        }
        if(secondsPart > 0L) {
            output.append(secondsPart)
                .append("s")
        }
        return output.toString().ifEmpty { "0s" }
    }
}
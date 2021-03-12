package moe.kabii.discord.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class DateValidation { VALID, OLD, NEW }

class SnowflakeParser private constructor(
    val timestamp: Long,
    val workerID: Short,
    val processID: Short,
    val increment: Short,
    val valiDate: DateValidation,
    val instant: Instant,
    val utc: LocalDateTime) {
    companion object {
        fun of(snowflake: Long): SnowflakeParser {
            // per discord api docs
            val discordEpoch = snowflake.shr(22)
            val epoch = discordEpoch + 1420070400000L
            val instant = Instant.ofEpochMilli(epoch)
            val datetime = instant.atZone(ZoneOffset.UTC).toLocalDateTime()
            val dateValidation = when {
                instant.isAfter(Instant.now().plusSeconds(5)) -> DateValidation.NEW
                discordEpoch < 0L -> DateValidation.OLD
                else -> DateValidation.VALID
            }
            return SnowflakeParser (
                timestamp = discordEpoch,
                workerID = snowflake.and(0x3E0000).shr(17).toShort(),
                processID = snowflake.and(0x1F000).shr(12).toShort(),
                increment = snowflake.and(0xFFF).toShort(),
                valiDate = dateValidation,
                instant = instant,
                utc = datetime
            )
        }
    }
}
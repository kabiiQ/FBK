package moe.kabii.discord.audio

import moe.kabii.discord.command.DiscordParameters
import moe.kabii.util.DurationParser

data class ExtractedQuery private constructor(val url: String, val timestamp: Long, val sample: Long?) {
    companion object {
        private val timestampRegex = Regex("[&?#](?:t|time)=([0-9smh]*)")
        private val sampleRegex = Regex("[&?]sample=([0-9smh]*)")

        fun from(origin: DiscordParameters): ExtractedQuery? {
            val attachment = origin.event.message.attachments.firstOrNull()
            if (attachment != null) {
                // if attached file, try to send this through first
                return default(attachment.url)
            }
            if (origin.args.isEmpty()) return null
            var url = origin.noCmd
            // extract timestamp
            val matchTime = timestampRegex.find(url)
            val timestamp = matchTime?.groups?.get(1)
            val time = timestamp?.run { DurationParser.tryParse(value) }
            url = if (matchTime != null) url.replace(matchTime.value, "") else url

            // extract sample length
            val matchSample = sampleRegex.find(url)
            val sampleTime = matchSample?.groups?.get(1)
            val sample = sampleTime?.run { DurationParser.tryParse(value) }
            url = if(matchSample != null) url.replace(matchSample.value, "") else url

            return ExtractedQuery(
                url = url,
                timestamp = time?.toMillis() ?: 0L,
                sample = sample?.toMillis()
            )
        }

        fun default(url: String): ExtractedQuery = ExtractedQuery(url, timestamp = 0L, sample = null)
    }
}
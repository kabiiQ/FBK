package moe.kabii.discord.audio

import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.MusicSettings
import moe.kabii.util.DurationParser
import moe.kabii.util.extensions.orNull

class ExtractedQuery private constructor(var url: String, val timestamp: Long, val sample: Long?, val volume: Int?) {
    init {
        // ignore <> if they surround a URL - these can be used in Discord to avoid embedding a link
        url = url.removeSurrounding("<", ">")
    }

    companion object {
        private val timestampRegex = Regex("[&?#](?:t|time)=([0-9smh]*)")
        private val sampleRegex = Regex("[&?]sample=([0-9smh]*)")
        private val volumeRegex = Regex("[&?]volume=([0-9]{1,3})")

        fun from(origin: DiscordParameters): ExtractedQuery {
            val attachment = origin.interaction.commandInteraction.orNull()?.resolved?.orNull()?.attachments?.values?.firstOrNull()
            if(attachment != null) {
                return default(attachment.url)
            }

            val songArg = origin.args.string("song")

            var url = songArg
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

            // extract volume param
            val matchVolume = volumeRegex.find(url)
            val volumePct = matchVolume?.groups?.get(1)
            val volume = volumePct?.value?.toIntOrNull()
            url = if(matchVolume != null) url.replace(matchVolume.value, "") else url

            val volumeArg = origin.args.optInt("volume")?.toInt()

            return ExtractedQuery(
                url = url,
                timestamp = time?.toMillis() ?: 0L,
                sample = sample?.toMillis(),
                volume = volumeArg ?: volume
            )
        }

        // used for attachments, playlists, or internally in 'search' results - input requires no processing
        fun default(url: String): ExtractedQuery = ExtractedQuery(url, timestamp = 0L, sample = null, volume = null)
    }
}
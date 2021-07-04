package moe.kabii.command.commands.search

import com.squareup.moshi.JsonClass
import discord4j.rest.util.Color
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.newRequestBuilder
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.EmbedBlock
import moe.kabii.util.extensions.stackTraceString
import org.apache.commons.lang3.StringUtils
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object XKCDLookup : Command("xkcd") {
    override val wikiPath = "Lookup-Commands#xkcd-comics"

    private val xkcdAdapter = MOSHI.adapter(XKCDResponse::class.java)
    private val dateFormat = DateTimeFormatter.ofPattern("M d yyyy")

    init {
        discord {
            channelFeatureVerify(FeatureChannel::searchCommands, "search")

            val arg = args.getOrNull(0)?.toIntOrNull()
            if(args.isEmpty() || arg != null) {

                // if no arg is provided (current comic) or a specific number is provided (lookup)
                val comicPart = if(arg != null) "/$arg" else ""
                val request = newRequestBuilder()
                    .get()
                    .url("http://xkcd.com$comicPart/info.0.json")
                    .build()

                val comic = try {
                    OkHTTP.newCall(request).execute().use { rs ->
                        if(rs.isSuccessful) {
                            val body = rs.body!!.string()
                            xkcdAdapter.fromJson(body)!!
                        } else {
                            error("Unable to find comic.").awaitSingle()
                            return@discord
                        }
                    }
                } catch (e: Exception) {
                    error("Unable to reach xkcd.").awaitSingle()
                    LOG.info(e.stackTraceString)
                    return@discord
                }

                val dateStr = "${comic.month} ${comic.day} ${comic.year}"
                val date = LocalDate.parse(dateStr, dateFormat).atStartOfDay().toInstant(ZoneOffset.UTC)

                val embed: EmbedBlock = {
                    setColor(Color.of(9873608))
                    val title = "xkcd #${comic.num}: ${comic.title}"
                    setAuthor(StringUtils.abbreviate(title, MagicNumbers.Embed.TITLE), "https://xkcd.com/${comic.num}", null)
                    setDescription(StringUtils.abbreviate(comic.alt, MagicNumbers.Embed.NORM_DESC))
                    setImage(comic.img)
                    setTimestamp(date)
                }
                chan.createEmbed(embed).awaitSingle()

            } else {
                // a non-number input was provided
                usage("The **xkcd** command is used to pull either the current or a specific comic from xkcd.com.", "xkcd (optional: comic number)").awaitSingle()
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class XKCDResponse(
        val num: Int,
        val title: String,
        val alt: String,
        val img: String,
        val day: String,
        val month: String,
        val year: String
    )
}
package moe.kabii.command.commands.search

import com.squareup.moshi.JsonClass
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.util.Color
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds
import moe.kabii.newRequestBuilder
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.stackTraceString
import org.apache.commons.lang3.StringUtils
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object XKCDLookup : Command("xkcd") {
    override val wikiPath = "Lookup-Commands#xkcd-comics-xkcd"

    private val xkcdAdapter = MOSHI.adapter(XKCDResponse::class.java)
    private val dateFormat = DateTimeFormatter.ofPattern("M d yyyy")

    init {
        chat {
            val idArg = args.optInt("id")

            // if no arg is provided (current comic) or a specific number is provided (lookup)
            val comicPart = if(idArg != null) "/$idArg" else ""
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
                        ereply(Embeds.error("Unable to find comic.")).awaitSingle()
                        return@chat
                    }
                }
            } catch (e: Exception) {
                ereply(Embeds.error("Unable to reach xkcd.")).awaitSingle()
                LOG.info(e.stackTraceString)
                return@chat
            }

            val dateStr = "${comic.month} ${comic.day} ${comic.year}"
            val date = LocalDate.parse(dateStr, dateFormat).atStartOfDay().toInstant(ZoneOffset.UTC)

            val title = "xkcd #${comic.num}: ${comic.title}"
            ireply(
                Embeds.other(StringUtils.abbreviate(comic.alt, MagicNumbers.Embed.NORM_DESC), Color.of(9873608))
                    .withAuthor(EmbedCreateFields.Author.of(StringUtils.abbreviate(title, MagicNumbers.Embed.TITLE), "https://xkcd.com/${comic.num}", null))
                    .withImage(comic.img)
                    .withTimestamp(date)
            ).awaitSingle()
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
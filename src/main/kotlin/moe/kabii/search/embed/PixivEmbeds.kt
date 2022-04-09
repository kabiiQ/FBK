package moe.kabii.search.embed

import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.OkHTTP
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.awaitAction
import okhttp3.Request

object PixivEmbeds : Command("pixiv") {
    override val wikiPath: String? = null // TODO

    private val pixivUrl = Regex("https://(?:www.)?pixiv.net/(?:en/)?artworks/(\\d{8,10})(\\|\\|)?")

    init {
        discord {

            val urlArg = args.string("url")
            val pid = pixivUrl.find(urlArg)?.groups?.get(1)?.value
            if(pid == null) {
                ereply(Embeds.error("This does not seem to be a valid Pixiv URL.")).awaitSingle()
                return@discord
            }

            val startIndex = args.optInt("start")?.minus(1) ?: 0
            val imageCount = args.optInt("count") ?: 6

            event.reply("<$urlArg>").awaitAction()
            for(i in startIndex..imageCount) {
                // try to pull image
                val imageUrl = "https://boe-tea-pximg.herokuapp.com/$pid/$i"
                val pixivRequest = Request.Builder()
                    .get()
                    .header("User-Agent", "srkmfbk/1.0")
                    .url(imageUrl)
                    .build()

                val success = try {
                    OkHTTP.newCall(pixivRequest).execute().use { response ->
                        response.code == 200
                    }
                } catch(e: Exception) {
                    false
                }
                if(!success) break
                event.createFollowup()
                    .withEmbeds(Embeds.other(Color.of(40191)).withImage(imageUrl))
                    .awaitSingle()
            }
        }
    }
}
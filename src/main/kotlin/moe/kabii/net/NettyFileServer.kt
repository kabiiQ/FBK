package moe.kabii.net

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.Keys
import moe.kabii.discord.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.discord.util.RGB
import okhttp3.Request
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import javax.imageio.ImageIO

object NettyFileServer {
    private val address = Keys.config[Keys.Netty.domain]
    private val port = Keys.config[Keys.Netty.port]

    val domain = "$address:$port"
    val staticRoot = File("files/images/")
    val idRoot = File("files/ids/")

    val defaultThumbnail = File(staticRoot, "default_twitch_thumbnail.png")

    init {
        LOG.info("Netty file server binding to port $port")
    }

    val server = embeddedServer(Netty, port = port) {
        staticRoot.mkdirs()
        idRoot.mkdirs()
        val udLogo = File(staticRoot, "ud_logo.jpg")
        val glitch = File(staticRoot, "Twitch_Glitch_Purple.png")
        val youtubeLogo = File(staticRoot, "youtube_social_circle_red.png")
        val twitterLogo = File(staticRoot, "twitter.png")
        val twitcastingLogo = File(staticRoot, "twitcasting_logo.png")

        routing {
            get("/thumbnails/twitch/{twitchname}/{...}") {
                val twitchName = call.parameters["twitchname"]
                if(twitchName != null) {
                    val thumbnailUrl = TwitchParser.getThumbnailUrl(twitchName)
                    val request = Request.Builder().get().url(thumbnailUrl).build()
                    try {
                        OkHTTP.newCall(request).execute().use { rs ->
                            call.respondBytes(rs.body!!.bytes(), contentType = ContentType.Image.PNG)
                        }
                    } catch(e: Exception) {
                        call.respondFile(defaultThumbnail)
                    }
                }
            }

            get("/color/{r}/{g}/{b}") {
                val r = call.parameters["r"]?.toIntOrNull()
                val g = call.parameters["g"]?.toIntOrNull()
                val b = call.parameters["b"]?.toIntOrNull()
                fun valid(int: Int?) = int in 0..255
                if (valid(r) && valid (g) && valid(b)) {
                    val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
                    val graphic = image.createGraphics()
                    graphic.color = Color(r!!, g!!, b!!)
                    graphic.fillRect(0, 0, 256, 256)
                    val output = ByteArrayOutputStream()
                    ImageIO.write(image, "png", output)
                    call.respondBytes(output.toByteArray(), ContentType.Image.PNG)
                }
            }

            get("/ids/{file}") {
                val file = call.parameters["file"]
                call.respondFile(File(idRoot, "$file"))
            }

            // static resources could just be a 'static' definition here - but I think there are few enough we can make nice short, controlled urls
            get ("/ud") {
                call.respondFile(udLogo)
            }
            get("/glitch") {
                call.respondFile(glitch)
            }
            get("/ytlogo") {
                call.respondFile(youtubeLogo)
            }
            get("/twitter") {
                call.respondFile(twitterLogo)
            }
            get("/twitcasting") {
                call.respondFile(twitcastingLogo)
            }
        }
    }

    val urbanDictionary = "$domain/ud"
    val glitch = "$domain/glitch"
    val youtubeLogo = "$domain/ytlogo"
    val twitterLogo = "$domain/twitter"
    val twitcastingLogo = "$domain/twitcasting"
    fun rgb(rgb: RGB): String {
        val (r, g, b) = rgb
        return "$domain/color/$r/$g/$b"
    }
    fun twitchThumbnail(id: String) = "$domain/thumbnails/twitch/$id/${Instant.now().epochSecond}}"

    fun ids(id: String) = "$domain/ids/$id.txt"
}
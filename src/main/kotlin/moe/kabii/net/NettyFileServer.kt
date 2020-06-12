package moe.kabii.net

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respondBytes
import io.ktor.response.respondFile
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.Keys
import moe.kabii.discord.trackers.streams.twitch.TwitchParser
import moe.kabii.rusty.Ok
import moe.kabii.util.RGB
import okhttp3.Request
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import javax.imageio.ImageIO

object NettyFileServer {
    private val port = Keys.config[Keys.Netty.port]

    val domain = "http://content.kabii.moe:$port"
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

        routing {
            get("/thumbnails/twitch/{twitchid}/{...}") {
                val twitchID = call.parameters["twitchid"]?.toLongOrNull()
                if (twitchID != null) {
                    val api = TwitchParser.getStream(twitchID)
                    if (api is Ok) {
                        val stream = api.value

                        val thumbnailURL = stream.rawThumbnail.replace("{width}x{height}", "1280x720")
                        val request = Request.Builder().get().url(thumbnailURL)
                        val image = OkHTTP.make(request) { response ->
                            response.body!!.bytes()
                        }
                        if(image is Ok) {
                            call.respondBytes(image.value, ContentType.Image.PNG)
                            return@get
                        }
                    }
                }
                call.respondFile(File(staticRoot, "default_twitch_thumbnail.png"))
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

            get("/ids/{file}.{...}") {
                val file = call.parameters["file"]
                call.respondFile(File(idRoot, "$file.txt"))
            }

            // static resources could just be a 'static' definition here - but I think there are few enough we can make nice short, controlled urls
            get ("/ud") {
                call.respondFile(udLogo)
            }
            get("/glitch") {
                call.respondFile(glitch)
            }
        }
    }

    val urbanDictionary = "$domain/ud"
    val smug = "$domain/smug"
    val glitch = "$domain/glitch"
    fun rgb(rgb: RGB): String {
        val (r, g, b) = rgb
        return "$domain/color/$r/$g/$b"
    }
    fun twitchThumbnail(id: Long) = "$domain/thumbnails/twitch/$id/${Instant.now().epochSecond}}"

    fun ids(id: String) = "$domain/ids/$id.txt"
    fun idsAll(id: String) = "$domain/ids/$id-all.txt"
}
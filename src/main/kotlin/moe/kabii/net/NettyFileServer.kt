package moe.kabii.net

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respondBytes
import io.ktor.response.respondFile
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import moe.kabii.helix.TwitchHelix
import moe.kabii.rusty.Ok
import moe.kabii.util.RGB
import okhttp3.Request
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

object NettyFileServer {
    val domain = "http://content.kabii.moe"
    val thumbnailRoot = File("files/thumbnails/")
    val staticRoot = File("files/images/")
    val idRoot = File("files/ids/")

    val defaultThumbnail = File(staticRoot, "default_twitch_thumbnail.png")

    val server = embeddedServer(Netty, port = 8080) {
        thumbnailRoot.mkdirs()
        staticRoot.mkdirs()
        idRoot.mkdirs()
        val udLogo = File(staticRoot, "ud_logo.jpg")
        val smug = File(staticRoot, "KizunaAi_Smug.png")
        val glitch = File(staticRoot, "Twitch_Glitch_Purple.png")

        routing {
            get("/thumbnails/{twitchid}/{...}") {
                val twitchID = call.parameters["twitchid"]?.toLongOrNull()
                if (twitchID != null) {
                    val api = TwitchHelix.getStream(twitchID)
                    if (api is Ok) {
                        val stream = api.value
                        val thumbnailURL = stream.thumbnail_url.replace("{width}x{height}", "1280x720")
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
                if (r != null && g != null && b != null) {
                    val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
                    val graphic = image.createGraphics()
                    graphic.color = Color(r, g, b)
                    graphic.fillRect(0, 0, 256, 256)
                    val output = ByteArrayOutputStream()
                    ImageIO.write(image, "png", output)
                    call.respondBytes(output.toByteArray(), ContentType.Image.PNG)
                }
            }

            get("/ids/{file}.txt") {
                val file = call.parameters["file"]
                call.respondFile(File(idRoot, "$file.txt"))
            }

            // static resources could just be a 'static' definition here - but I think there are few enough we can make nice short, controlled urls
            get ("/ud") {
                call.respondFile(udLogo)
            }
            get("/smug") {
                call.respondFile(smug)
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
    fun thumbnail(id: Long) = "$domain/thumbnails/$id"

    fun ids(id: String) = "$domain/ids/$id.txt"
    fun idsAll(id: String) = "$domain/ids/$id-all.txt"
}
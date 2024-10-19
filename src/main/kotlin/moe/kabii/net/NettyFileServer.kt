package moe.kabii.net

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.discord.util.RGB
import moe.kabii.newRequestBuilder
import moe.kabii.trackers.nitter.NitterChecker
import moe.kabii.trackers.nitter.NitterParser
import moe.kabii.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.util.extensions.propagateTransaction
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.DecimalFormat
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.min

object NettyFileServer {
    private val address = Keys.config[Keys.Netty.domain]
    private const val port = 8080

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
        val kickLogo = File(staticRoot, "kick_logo_black.png")

        routing {
            get("/thumbnails/twitch/{twitchname}/{...}") {
                val twitchName = call.parameters["twitchname"]
                if(twitchName != null) {
                    val thumbnailUrl = TwitchParser.getThumbnailUrl(twitchName)
                    val request = newRequestBuilder().get().url(thumbnailUrl).build()
                    try {
                        OkHTTP.newCall(request).execute().use { rs ->
                            call.respondBytes(rs.body.bytes(), contentType = ContentType.Image.PNG)
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
            get("/kick") {
                call.respondFile(kickLogo)
            }

            get("/twitterfeeds") {
                val (priorityList, generalCount) = propagateTransaction {
                    val (priority, general) = TwitterFeed.all()
                        .sortedByDescending(TwitterFeed::id)
                        .partition { feed -> feed.enabled }
                    priority.map(TwitterFeed::username) to general.size
                }

                // Estimate refresh delays
                val priorityInstance = min(ceil((priorityList.size.toDouble() * NitterChecker.callDelay) / NitterChecker.refreshGoal).toInt(), NitterParser.instanceCount)
                val generalInstance = NitterParser.instanceCount - priorityInstance

                // feed count * time per call / num instances / 60000 (millis to minutes)
                val priorityRefresh = (priorityList.size.toDouble() * NitterChecker.callDelay) / (priorityInstance * 60_000L)
                val generalRefresh = (generalCount.toDouble() * NitterChecker.callDelay) / (generalInstance * 60_000L)
                val format = DecimalFormat("#.##")

                call.respondHtml {
                    head {
                        title {
                            +"FBK Twitter Feed Status"
                        }
                    }
                    body {
                        h2 {
                            +"\"General\" tracked Twitter feeds = $generalCount"
                        }
                        h3 {
                            +"Refresh time estimate = ${format.format(generalRefresh)} minutes (not including potential Discord limitations)"
                        }
                        h2 {
                            +"\"Priority\" tracked Twitter feeds = ${priorityList.size}"
                        }
                        h3 {
                            +"Refresh time estimate = ${format.format(priorityRefresh)} minutes (not including potential Discord limitations)"
                        }
                        br()
                        table {
                            tr {
                                th {
                                    +"\"Priority\" Twitter feeds"
                                }
                            }
                            priorityList.forEach { username ->
                                tr {
                                    td {
                                        +username
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val urbanDictionary = "$domain/ud"
    val glitch = "$domain/glitch"
    val youtubeLogo = "$domain/ytlogo"
    val twitterLogo = "$domain/twitter"
    val twitcastingLogo = "$domain/twitcasting"
    val kickLogo = "$domain/kick"
    fun rgb(rgb: RGB): String {
        val (r, g, b) = rgb
        return "$domain/color/$r/$g/$b"
    }
    fun twitchThumbnail(id: String) = "$domain/thumbnails/twitch/$id/${Instant.now().epochSecond}"

    fun ids(id: String) = "$domain/ids/$id.txt"
}
package moe.kabii.discord.trackers.streams.youtube

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.kabii.LOG
import moe.kabii.data.Keys
import moe.kabii.structure.extensions.stackTraceString
import org.openqa.selenium.By
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

class YoutubeChannelError(override val message: String, val ytText: String, override val cause: Throwable? = null) : IOException()

// instanced to re-use browser for single scraping 'session'
class YoutubeScraper : AutoCloseable {

    companion object {
        private val matchUrl = Regex("/watch\\?v=([a-zA-Z0-9-_]{11})")

        private val chromeOptions: ChromeOptions
        private val chromeService: ChromeDriverService

        init {
            val headless = Keys.config[Keys.Selenium.headless]

            // setting this seems to be required on linux or when running from ide
            val driver = Keys.config[Keys.Selenium.chromeDriver]
            if(driver.isNotBlank()) {
                System.setProperty("webdriver.chrome.driver", driver)
            }

            // remove some logs from console each time chromedriver starts
            val chromeLog = File("logs/chromedriver.log")
            if(chromeLog.exists()) {
                chromeLog.delete()
            }
            chromeLog.createNewFile()

            chromeOptions = ChromeOptions()
                .setHeadless(headless)
                .addArguments("--log-level=3")
                .addArguments("--silent")
            chromeService = ChromeDriverService.Builder().build()

            chromeService.sendOutputTo(FileOutputStream(chromeLog))
            Logger.getLogger("org.openqa.selenium").level = Level.WARNING
        }
    }

    private val mutex = Mutex()
    private val chrome: ChromeDriver

    init {
        try {
            chrome = ChromeDriver(chromeService, chromeOptions)
        } catch(e: Exception) {
            LOG.error("Exception while launching chrome : ${e.message}")
            LOG.info(e.stackTraceString)
            throw e
        }
    }

    // this type of scraping is subject to breaking at any time with YouTube changes
    @Throws(YoutubeChannelError::class, IOException::class)
    suspend fun getLiveStream(channelId: String): YoutubeVideoInfo? {
        mutex.withLock {
            /*
            Open the channel in browser - at this time, there is an arbitrary delay for the JS elements to be loaded
            unsure how to handle this properly, selenium blocks until the page 'loads' but the elements we are matching on -
            which may or may not exist, do not necessarily load immediately.
             */
            chrome.navigate().to("https://youtube.com/channel/$channelId")
            delay(500L)

            // check for YouTube error - the channel should definitely exist to reach this point, but may be terminated, suspended, etc...
            val error = chrome.findElementsByCssSelector(".ERROR.yt-alert-renderer").firstOrNull()
            if(error != null) {
                val err = error.text.trim()
                throw YoutubeChannelError("Youtube channel '$channelId' returned an error: $err", err)
            }

            // if a channel is live - it will be the first video on the /channel/ page (obviously, you want people to see you are live)
            // there are currently two different badges YouTube may return to represent a live channel - seemingly at random (likely an intentional A/B test)

            // the old youtube 'LIVE NOW' badge element
            val liveNowBadge = chrome.findElementsByXPath("//*[@id=\"badges\"]/div").firstOrNull()

            // new live badge style uses the same element that overlays the video thumbnails - where the video length would be displayed
            // so, merely checking for this element will do no good, there will be many on the page - one for each video in view
            val newLiveBadge = chrome.findElementsByXPath("//*[@id=\"overlays\"]/ytd-thumbnail-overlay-time-status-renderer")
                .firstOrNull { element ->
                    // furthermore, checking simply for the badge to read 'LIVE' is still insufficient for channels with 'upcoming' streams
                    // with the new style, upcoming streams also read 'LIVE', but without the yt logo next to them
                    element.text == "LIVE" && element.getAttribute("has-icon") != null
                }

            val liveElement = liveNowBadge ?: newLiveBadge
            if(liveElement == null) {
                // channel is not live
                return null
            }

            // go up a few levels, find the thumbnail element, and extract the video ID
            val videoElement = liveElement.findElements(By.xpath("./../../../../../..")).first()
            val thumbnailElement = videoElement.findElements(By.xpath(".//*[@id=\"thumbnail\"]")).first()
            val videoUrl = thumbnailElement.getAttribute("href")!!
            val videoId = matchUrl.find(videoUrl)!!.groupValues[1]

            // since we have scraped the page, we have all the information we need about this stream
            // we can find video-specific information on this page and avoid a call to /videos/
            val thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault_live.jpg"

            val titleElement = videoElement.findElements(By.xpath(".//*[@id=\"video-title\"]")).firstOrNull()
            val title = titleElement?.getAttribute("title").orEmpty()

            val descriptionElement = videoElement.findElements(By.xpath(".//*[@id=\"description-text\"]")).firstOrNull()
            val description = descriptionElement?.text.orEmpty()

            // these are not specific to the videoElement - but save us call to /channel/ api
            val nameElement = chrome.findElementsByClassName("ytd-channel-name").firstOrNull()
            val channelName = nameElement?.text.orEmpty()

            val bannerElement = chrome.findElementsByXPath("//*[@id=\"avatar\"]").firstOrNull()
            val avatarElement = bannerElement?.findElements(By.xpath(".//*[@id=\"img\"]"))?.firstOrNull()
            val channelAvatar = avatarElement?.getAttribute("src")

            return YoutubeVideoInfo(
                id = videoId,
                title = title,
                description = description,
                thumbnail = thumbnailUrl,
                live = true,
                duration = null,
                YoutubeChannelInfo(
                    id = channelId,
                    name = channelName,
                    avatar = channelAvatar
                )
            )
        }
    }

    override fun close() {
        chrome.quit()
    }
}
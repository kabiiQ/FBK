package moe.kabii.discord.trackers.videos.youtube

import discord4j.rest.util.Color
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.discord.trackers.videos.StreamErr
import moe.kabii.discord.trackers.videos.youtube.json.YoutubeChannelResponse
import moe.kabii.discord.trackers.videos.youtube.json.YoutubeErrorResponse
import moe.kabii.discord.trackers.videos.youtube.json.YoutubeVideoResponse
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import java.io.IOException

class YoutubeAPIException(message: String, cause: Throwable? = null) : IOException(message, cause)

object YoutubeParser {
    private val apiKey = Keys.config[Keys.Youtube.key]
    private val errorAdapter = MOSHI.adapter(YoutubeErrorResponse::class.java)

    val color = Color.of(16711680)

    val youtubeChannelPattern = Regex("([a-zA-Z0-9-_]{24})")
    val youtubeNamePattern = Regex("([a-zA-Z0-9)]{6,20})")

    val youtubeVideoPattern = Regex("([a-zA-Z0-9-_]{11})")
    val youtubeVideoUrlPattern = Regex("(?:(?:youtu\\.be/|v/|vi/|u/\\w/|embed/|shorts/)|(?:(?:watch)?\\?vi?=|&vi?=))($youtubeVideoPattern)")

    @Throws(YoutubeAPIException::class)
    fun getChannelFromUnknown(identifier: String): YoutubeChannelInfo? {
        return when {
            identifier.length in 6..20 && identifier.matches(youtubeNamePattern) -> getChannelByName(identifier)
            identifier.length == 24 && identifier.matches(youtubeChannelPattern) -> getChannelById(identifier)
            else -> null
        }
    }

    @Throws(YoutubeAPIException::class)
    private fun getChannelById(channelId: String): YoutubeChannelInfo? = getChannel("id=$channelId")

    @Throws(YoutubeAPIException::class)
    private fun getChannelByName(name: String): YoutubeChannelInfo? = getChannel("forUsername=$name")

    @Throws(YoutubeAPIException::class)
    private fun getChannel(identifierPart: String): YoutubeChannelInfo? {
        val request = requestJson<YoutubeChannelResponse>("channels?part=snippet&$identifierPart")

        return request.items.firstOrNull()?.let { channel ->
            YoutubeChannelInfo(
                id = channel.id,
                name = channel.snippet.title,
                avatar = channel.snippet.thumbnails.default.url
            )
        }
    }

    fun matchVideoId(input: String): String? {
        // take possible youtube video ID or URL
        return if(input.matches(youtubeVideoPattern)) input
        else youtubeVideoUrlPattern.find(input)?.groups?.get(1)?.value
    }

    fun getVideo(videoId: String): YoutubeVideoInfo? =
        getVideos(listOf(videoId)).values.single().orNull()

    fun getVideos(videoIds: List<String>): Map<String, Result<YoutubeVideoInfo, StreamErr>> {
        // we are able to chunk request up to 50 video IDs from 1 API call (yt limit for non-paginated endpoint)
        return videoIds.chunked(50).map { idChunk ->
            val idsPart = idChunk.joinToString(",")
            val request = try {
                requestJson<YoutubeVideoResponse>("videos?part=snippet,contentDetails,liveStreamingDetails&id=$idsPart")
            } catch(e: Exception) {
                LOG.warn("Error making YouTube request: ${e.message}")
                LOG.debug(e.stackTraceString)
                return@map idChunk.map { it to Err(StreamErr.IO) }
            }

            // match each returned video back to the request. this iteration could be skipped but we want to also determine which videos did NOT return
            // every requested ID must be returned by this method
            val foundVideos = request.items
            idChunk.map { requestedId ->
                // for each requested video, check the found videos
                val match = foundVideos.find { foundVideo ->
                    requestedId == foundVideo.id
                }
                val value = if(match != null) {
                    val video = YoutubeVideoInfo(
                        id = match.id,
                        title = match.snippet.title,
                        description = match.snippet.description,
                        thumbnail = match.snippet.thumbnails.thumbnail(match.id),
                        live = match.snippet.live,
                        upcoming = match.snippet.upcoming,
                        premiere = match.premiere,
                        duration = match.contentDetails.duration,
                        published = match.snippet.publishedAt,
                        liveInfo = if(match.liveStreamingDetails != null) YoutubeStreamInfo(
                            startTime = match.liveStreamingDetails.startTime,
                            concurrent = match.liveStreamingDetails.concurrentViewers,
                            endTime = match.liveStreamingDetails.endTime,
                            scheduledStart = match.liveStreamingDetails.scheduledStartTime
                        ) else null,
                        channel = YoutubeChannelInfo(
                            id = match.snippet.channelId,
                            name = match.snippet.channelTitle,
                            avatar = null
                        )
                    )
                    Ok(video)
                } else Err(StreamErr.NotFound)
                requestedId to value
            }
        }.flatten().toMap()
    }


    @Throws(YoutubeAPIException::class, IOException::class)
    private inline fun <reified R: Any> requestJson(requestPart: String): R {
        val requestUrl = "https://www.googleapis.com/youtube/v3/$requestPart&key=$apiKey"

        val request = Request.Builder()
            .get()
            .url(requestUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "srkmfbk/1.0")
            .build()

        try {
            val response = OkHTTP.newCall(request).execute()

            try {
                if (response.isSuccessful) {
                    // should receive relevant json
                    val body = response.body!!.string()
                    return MOSHI.adapter(R::class.java).fromJson(body)!!

                } else {
                    // should receive error json
                    val body = response.body!!.string()
                    val error = errorAdapter.fromJson(body)
                        ?.error?.errors?.firstOrNull()
                    if (error == null) {
                        LOG.debug("Youtube JSON unknown error: ${response.code} :: $body :: $response")
                        throw YoutubeAPIException("Youtube JSON error parsing :: $body")
                    } else {

                        if (error.reason == "quotaExceeded" || error.reason == "dailyLimitExceeded") {
                            // if this triggers on video/channel calls, we will need to increase delay between calls
                            // and hopefully request increased quota from YT. set a flag to stop requests for the day
                            LOG.error("Youtube Quota exceeded : $error")
                        } else {
                            LOG.warn("Youtube call returned an error: $error")
                        }
                        LOG.trace("youtube error response body: $body")
                        throw YoutubeAPIException(error.toString())
                    }
                }
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            // probable actual network error, youtube should always return json. let the loop try once more
            LOG.debug("Youtube call generated ${e.message} :: $requestUrl")
            LOG.debug(e.stackTraceString)
            throw YoutubeAPIException("No usable response obtained", cause = e)
        }
    }
}
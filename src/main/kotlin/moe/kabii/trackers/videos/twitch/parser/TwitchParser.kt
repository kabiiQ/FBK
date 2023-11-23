package moe.kabii.trackers.videos.twitch.parser

import discord4j.rest.util.Color
import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.data.relational.streams.twitch.TwitchEventSubscriptions
import moe.kabii.net.NettyFileServer
import moe.kabii.newRequestBuilder
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.trackers.videos.StreamErr
import moe.kabii.trackers.videos.twitch.TwitchGameInfo
import moe.kabii.trackers.videos.twitch.TwitchStreamInfo
import moe.kabii.trackers.videos.twitch.TwitchUserInfo
import moe.kabii.trackers.videos.twitch.json.Helix
import moe.kabii.trackers.videos.twitch.json.TwitchEvents
import moe.kabii.trackers.videos.twitch.json.TwitchGameResponse
import moe.kabii.trackers.videos.twitch.json.TwitchStreamRequest
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import java.time.Duration
import java.time.Instant

object TwitchParser {
    val color = Color.of(6570405)

    val clientID = Keys.config[Keys.Twitch.client]
    val oauth = TwitchAuthorization()

    private val gameCache = mutableMapOf<Long, TwitchGameInfo>()

    private suspend inline fun <reified R: Any>  request(requestBuilder: Request.Builder): Result<R, StreamErr> {
        val builder = requestBuilder
            .header("Client-ID", clientID)

        for(attempt in 1..3) {
            try {
                val request = builder
                    .header("Authorization", oauth.authorization)
                    .build()

                val response = OkHTTP.newCall(request).execute()

                try {
                    if (!response.isSuccessful) {
                        val timeout = if (response.code == 429) {

                            val reset = response.headers.get("Ratelimit-Reset")?.toLong()
                            if (reset != null) {
                                Duration.between(Instant.now(), Instant.ofEpochSecond(reset)).toMillis()
                            } else 10_000L // retry after rate limit delay
                        } else if (response.code == 401) {
                            // require new api token
                            delay(200L)
                            oauth.refreshOAuthToken()
                            // retry with new oauth token
                            200L
                        } else {
                            LOG.error("Error calling Twitch API: ${request.url.encodedPath} :: ${response.body.string()}")
                            delay(200L)
                            break
                        }
                        delay(timeout)
                        continue
                    } else {
                        val body = response.body.string()
                        return try {
                            val json = MOSHI.adapter(R::class.java).fromJson(body)
                            if (json != null) Ok(json) else Err(StreamErr.NotFound)
                        } catch (e: Exception) {
                            LOG.error("Invalid JSON provided from Twitch: ${e.message} :: $body")
                            // api issue
                            Err(StreamErr.IO)
                        }
                    }
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                // actual network issue, retry
                LOG.warn("TwitchParser: Error reaching Twitch: ${e.message}")
                LOG.trace(e.stackTraceString)
                delay(2000L)
                continue
            }
        }
        return Err(StreamErr.IO) // if 3 attempts failed
    }

    suspend fun getUsers(ids: Collection<Long>): Map<Long, Result<TwitchUserInfo, StreamErr>> {
        val userLists = ids.chunked(100).map { chunk ->
            val users = chunk.joinToString("&id=")
            val request = newRequestBuilder().get().url("https://api.twitch.tv/helix/users?first=100&id=$users")
            val call = request<Helix.UserResponse>(request)
            if (call is Ok) {
                val responseUsers = call.value.data
                ids.map { requestID ->
                    // find the user with the same id in the request and response
                    val match = responseUsers.find { responseUser -> responseUser.id.toLong() == requestID }
                    // map to twitchid, streamuser
                    requestID to if (match != null) {
                        Ok(TwitchUserInfo(match.id.toLong(), match.login, match.display_name, match.profile_image_url))
                    } else Err(StreamErr.NotFound)
                }
            } else ids.map { it to Err(StreamErr.IO) } // call failed
        }
        return(userLists.flatten().toMap())
    }

    suspend fun getUser(id: Long): Result<TwitchUserInfo, StreamErr> =
        getUsers(listOf(id)).values.single()

    suspend fun getUser(name: String): Result<TwitchUserInfo, StreamErr> {
        val request = newRequestBuilder().get().url("https://api.twitch.tv/helix/users?login=$name")
        val call = request<Helix.UserResponse>(request)
        return if(call is Ok) {
            val user = call.value.data.getOrNull(0)
            if(user != null) {
                Ok(TwitchUserInfo(user.id.toLong(), user.login, user.display_name, user.profile_image_url))
            } else Err(StreamErr.NotFound)
        } else Err(StreamErr.IO)
    }

    suspend fun getStreams(ids: Collection<Long>): Map<Long, Result<TwitchStreamInfo, StreamErr>> {
        val streamLists = ids.chunked(100).map { chunk ->
            val streams = chunk.joinToString("&user_id=")
            val request = newRequestBuilder().get().url("https://api.twitch.tv/helix/streams/?first=100&user_id=$streams")
            val call = request<TwitchStreamRequest>(request)
            if(call is Ok) {
                val responseStreams = call.value.data
                chunk.map { requestID ->
                    // find the stream for each user in the requestH
                    val match = responseStreams.find { responseStream -> responseStream.userID == requestID }
                    requestID to if(match != null) {
                        Ok(match.asStreamInfo())
                    } else Err(StreamErr.NotFound)
                }
            } else ids.map { it to Err(StreamErr.IO) }
        }
        return(streamLists.flatten().toMap())
    }

    suspend fun getStream(id: Long): Result<TwitchStreamInfo, StreamErr> = getStreams(listOf(id)).values.single()

    suspend fun getGame(id: Long): TwitchGameInfo =
        gameCache.getOrPut(id) { getGameInfo(id) }

    private suspend fun getGameInfo(id: Long): TwitchGameInfo {
        if (id == 0L) return TwitchGameInfo(
            "0",
            "Nothing",
            NettyFileServer.glitch
        )
        val request = newRequestBuilder().get().url("https://api.twitch.tv/helix/games?id=$id")
        val call = request<TwitchGameResponse>(request)
        if(call is Ok) {
            val game = call.value.data.getOrNull(0)
            if(game != null) return TwitchGameInfo(game.id, game.name, game.boxArtURL)
        }
        return TwitchGameInfo("-1", "Unknown", NettyFileServer.glitch)
    }

    fun getThumbnailUrl(username: String) = "https://static-cdn.jtvnw.net/previews-ttv/live_user_$username-1280x720.jpg"

    object EventSub {

        suspend fun createSubscription(type: TwitchEventSubscriptions.Type, twitchUserId: Long): String? {
            val body = TwitchEvents.Request.Subscription.generateRequestBody(type, twitchUserId.toString())
            val request = Request.Builder()
                .url("https://api.twitch.tv/helix/eventsub/subscriptions")
                .post(body)
            return request<TwitchEvents.Response.EventSubResponse>(request).orNull()?.data?.firstOrNull()?.id
        }

        suspend fun deleteSubscription(subscriptionId: String): Boolean {
            val request = Request.Builder()
                .url("https://api.twitch.tv/helix/eventsub/subscriptions?id=$subscriptionId")
                .delete()
            return request<TwitchEvents.Response.DeleteResponse>(request).ok
        }
    }
}
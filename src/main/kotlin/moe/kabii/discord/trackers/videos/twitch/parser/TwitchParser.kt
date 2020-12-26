package moe.kabii.discord.trackers.videos.twitch.parser

import discord4j.rest.util.Color
import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.Keys
import moe.kabii.discord.trackers.videos.StreamErr
import moe.kabii.discord.trackers.videos.twitch.TwitchGameInfo
import moe.kabii.discord.trackers.videos.twitch.TwitchStreamInfo
import moe.kabii.discord.trackers.videos.twitch.TwitchUserInfo
import moe.kabii.discord.trackers.videos.twitch.json.Helix
import moe.kabii.discord.trackers.videos.twitch.json.TwitchGameResponse
import moe.kabii.discord.trackers.videos.twitch.json.TwitchStreamRequest
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.structure.extensions.stackTraceString
import okhttp3.Request
import java.time.Duration
import java.time.Instant

object TwitchParser {
    val color = Color.of(6570405)

    private val clientID = Keys.config[Keys.Twitch.client]
    private val oauth = Authorization()

    private suspend inline fun <reified R: Any>  request(requestStr: String): Result<R, StreamErr> {
        val builder = Request.Builder()
            .get()
            .url(requestStr)
            .header("Client-ID", clientID)
            .header("User-Agent", "srkmfbk/1.0")

        for(attempt in 1..3) {
            try {
                val request = builder
                    .header("Authorization", "Bearer ${oauth.accessToken}")
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
                            val newToken = oauth.refreshOAuthToken()
                            newToken.ifErr { e ->
                                LOG.warn("Error refreshing Twitch OAuth token: ${e.message}")
                                LOG.debug(e.stackTraceString)
                            }
                            // retry with new oauth token
                            200L
                        } else {
                            LOG.error("Error calling Twitch API: $response")
                            // retry call
                            200L
                        }
                        delay(timeout)
                        continue
                    } else {
                        val body = response.body!!.string()
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
            val call =
                request<Helix.UserResponse>("https://api.twitch.tv/helix/users?first=100&id=$users")
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
        val call =
            request<Helix.UserResponse>("https://api.twitch.tv/helix/users?login=$name")
        if(call is Ok) {
            val user = call.value.data.getOrNull(0)
            return if(user != null) {
                Ok(TwitchUserInfo(user.id.toLong(), user.login, user.display_name, user.profile_image_url))
            } else Err(StreamErr.NotFound)
        } else return Err(StreamErr.IO)
    }

    suspend fun getStreams(ids: Collection<Long>): Map<Long, Result<TwitchStreamInfo, StreamErr>> {
        val streamLists = ids.chunked(100).map { chunk ->
            val streams = chunk.joinToString("&user_id=")
            val call =
                request<TwitchStreamRequest>("https://api.twitch.tv/helix/streams/?first=100&user_id=$streams")
            if(call is Ok) {
                val responseStreams = call.value.data
                ids.map { requestID ->
                    // find the stream for each user in the request
                    val match = responseStreams.find { responseStream -> responseStream.userID == requestID }
                    requestID to if(match != null) {
                        Ok(TwitchStreamInfo(match.userID, match.username, match.title, match.viewers, match.startedAt, match.thumbnail, match.gameID))
                    } else Err(StreamErr.NotFound)
                }
            } else ids.map { it to Err(StreamErr.IO) }
        }
        return(streamLists.flatten().toMap())
    }

    suspend fun getStream(id: Long): Result<TwitchStreamInfo, StreamErr> = getStreams(listOf(id)).values.single()

    suspend fun getGame(id: Long): TwitchGameInfo {
        if (id == 0L) return TwitchGameInfo(
            "0",
            "Nothing",
            NettyFileServer.glitch
        )
        val call =
            request<TwitchGameResponse>("https://api.twitch.tv/helix/games?id=$id")
        if(call is Ok) {
            val game = call.value.data.getOrNull(0)
            if(game != null) return TwitchGameInfo(game.id, game.name, game.boxArtURL)
        }
        return TwitchGameInfo("-1", "Unknown", NettyFileServer.glitch)
    }

    fun getThumbnailUrl(username: String) = "https://static-cdn.jtvnw.net/previews-ttv/live_user_$username-1280x720.jpg"
}
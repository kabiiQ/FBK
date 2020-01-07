package moe.kabii.discord.trackers.streams.twitch

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.Keys
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.trackers.streams.*
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.structure.fromJsonSafe
import okhttp3.Request
import java.awt.Color
import java.time.Duration
import java.time.Instant

object TwitchParser : StreamParser {
    override val site by lazy { TrackedStreams.Site.TWITCH }
    override val color = Color(6570405)
    override val icon: String = NettyFileServer.glitch

    class TwitchUser(userID: Long, username: String, displayName: String, profileImage: String)
        : StreamUser(this, userID, username, displayName, profileImage) {
        override val thumbnailUrl: String
        get() = NettyFileServer.twitchThumbnail(userID)
        override val url: String = "https://twitch.tv/$username"
    }

    private inline fun <reified R: Any>  request(request: String): Result<R, StreamErr> {
        val request = Request.Builder()
            .get()
            .url(request)
            .header("Client-ID", Keys.config[Keys.Twitch.client])
        for(attempt in 1..3) {
            val response = OkHTTP.make(request) { response ->
                if (!response.isSuccessful) {
                    if (response.code == 429) {
                        val reset = response.headers.get("Ratelimit-Reset")?.toLong()
                        val timeout = if (reset != null) {
                            Duration.between(Instant.now(), Instant.ofEpochSecond(reset)).toMillis()
                        } else 12000L
                        Thread.sleep(timeout) // rate limit retry later
                        null
                    } else {
                        LOG.error("Error getting Twitch call: $response")
                        null
                    }
                } else {
                    val body = response.body!!.string()
                    when(val json = MOSHI.adapter(R::class.java).fromJsonSafe(body)) {
                        is Ok -> json.value
                        is Err -> {
                            LOG.error("Invalid JSON provided from Twitch: ${json.value} :: $body}")
                            null
                        }
                    }
                }
            }
            if(response is Ok) {
                val json = response.value
                return if(json != null) Ok(json) else Err(StreamErr.NotFound)
            } else {
                // actual network issue
                Thread.sleep(1000L)
            }
        }
        return Err(StreamErr.IO) // if 3 attempts failed
    }

    override fun getUsers(ids: Collection<Long>): Map<Long, Result<StreamUser, StreamErr>> {
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
                        Ok(TwitchUser(match.id.toLong(), match.login, match.display_name, match.profile_image_url))
                    } else Err(StreamErr.NotFound)
                }
            } else ids.map { it to Err(StreamErr.IO) } // call failed
        }
        return(userLists.flatten().toMap())
    }

    override fun getUser(id: Long): Result<StreamUser, StreamErr> =
        getUsers(listOf(id)).values.single()

    override fun getUser(name: String): Result<StreamUser, StreamErr> {
        val call =
            request<Helix.UserResponse>("https://api.twitch.tv/helix/users?login=$name")
        if(call is Ok) {
            val user = call.value.data.getOrNull(0)
            return if(user != null) {
                Ok(TwitchUser(user.id.toLong(), user.login, user.display_name, user.profile_image_url))
            } else Err(StreamErr.NotFound)
        } else return Err(StreamErr.IO)
    }

    override fun getStreams(ids: Collection<Long>): Map<Long, Result<StreamDescriptor, StreamErr>> {
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
                        Ok(object : StreamDescriptor(this, match.userID.toString(), match.username, match.title, match.viewers, match.startedAt, match.thumbnail) {
                            // Twitch does not return this information when getting a stream unlike mixer,
                            // so we make a request when this information is needed for Twitch streams.
                            override val game by lazy {
                                getGame(match.gameID)
                            }
                            override val user by lazy {
                                getUser(requestID).unwrap()
                            }
                        })
                    } else Err(StreamErr.NotFound)
                }
            } else ids.map { it to Err(StreamErr.IO) }
        }
        return(streamLists.flatten().toMap())
    }

    override fun getStream(id: Long): Result<StreamDescriptor, StreamErr> = getStreams(listOf(id)).values.single()

    private fun getGame(id: Long): StreamGame {
        if (id == 0L) return StreamGame(
            "0",
            "Nothing",
            NettyFileServer.glitch
        )
        val call =
            request<TwitchGameResponse>("https://api.twitch.tv/helix/games?id=$id")
        if(call is Ok) {
            val game = call.value.data.getOrNull(0)
            if(game != null) return StreamGame(game.id, game.name, game.boxArtURL)
        }
        return StreamGame("-1", "Unknown", NettyFileServer.glitch)
    }
}
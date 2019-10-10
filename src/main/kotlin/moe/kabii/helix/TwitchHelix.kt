package moe.kabii.helix

import com.beust.klaxon.Klaxon
import moe.kabii.data.Keys
import moe.kabii.net.NettyFileServer
import moe.kabii.net.OkHTTP
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import okhttp3.Request
import java.awt.Color
import java.time.Duration
import java.time.Instant

object TwitchHelix {
    val klaxon = Klaxon()
    val color = Color(6570405)

    inline fun <reified R: Any>  request(request: String): Result<R, HelixAPIErr> {
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
                        println("Error getting Twitch call: $response")
                        null
                    }
                } else {
                    val body = response.body!!.string()
                    klaxon.parse<R>(body)
                }
            }
            if(response is Ok) {
                val json = response.value
                return if(json != null) Ok(json) else Err(EmptyObject)
            } else {
                // actual network issue
                Thread.sleep(1000L)
            }
        }
        return Err(HelixIOErr) // if 3 attempts failed
    }

    fun getUsers(ids: Collection<Long>): Map<Long, Result<TwitchUser, HelixAPIErr>> {
        val userLists = ids.chunked(100).map { chunk ->
            val users = chunk.joinToString("&id=")
            val call =
                request<TwitchUserResponse>("https://api.twitch.tv/helix/users?first=100&id=$users")
            if (call is Ok) {
                val responseUsers = call.value.data
                ids.map  { requestID ->
                    // find the user with the same id in the request and response
                    val match = responseUsers.find { responseUser -> responseUser.id.toLong() == requestID }
                    // map to twitchid, twitchuser
                    requestID to if(match != null) Ok(match) else Err(EmptyObject)
                }
            } else ids.map { it to Err(HelixIOErr) } // call failed
        }
        return(userLists.flatten().toMap())
    }


    fun getUser(name: String): Result<TwitchUser, HelixAPIErr> {
        val call =
            request<TwitchUserResponse>("https://api.twitch.tv/helix/users?login=$name")
        if(call is Ok) {
            val user = call.value.data.getOrNull(0)
            return if(user != null) Ok(user) else Err(EmptyObject)
        } else return Err(HelixIOErr)
    }

    fun getUser(id: Long): Result<TwitchUser, HelixAPIErr> = getUsers(listOf(id))
        .values.single()

    fun getStreams(ids: Collection<Long>): Map<Long, Result<TwitchStream, HelixAPIErr>> {
        val streamLists = ids.chunked(100).map { chunk ->
            val streams = chunk.joinToString("&user_id=")
            val call =
                request<TwitchStreamRequest>("https://api.twitch.tv/helix/streams/?first=100&user_id=$streams")
            if(call is Ok) {
                val responseStreams = call.value.data
                ids.map { requestID ->
                    // find the stream for each user in the request
                    val match = responseStreams.find { responseStream -> responseStream.userID == requestID }
                    requestID to if(match != null) Ok(match) else Err(EmptyObject)
                }
            } else ids.map { it to Err(HelixIOErr) }
        }
        return(streamLists.flatten().toMap())
    }

    fun getStream(id: Long) = getStreams(listOf(id)).values.single()

    fun getGame(id: Long): TwitchGame {
        if (id == 0L) return TwitchGame("0", "Nothing", NettyFileServer.glitch)
        val call =
            request<TwitchGameResponse>("https://api.twitch.tv/helix/games?id=$id")
        if(call is Ok) {
            val gameResponse = call.value.data.getOrNull(0)
            if(gameResponse != null) return gameResponse
        }
        return TwitchGame("-1", "Unknown", NettyFileServer.glitch)
    }
}
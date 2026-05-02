package moe.kabii.trackers.posts.holoplus.parser

import discord4j.rest.util.Color
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.command.commands.trackers.util.GlobalTrackSuggestionGenerator
import moe.kabii.newRequestBuilder
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.trackers.HoloplusTarget
import moe.kabii.trackers.TrackerErr
import moe.kabii.trackers.posts.holoplus.json.*
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request

object HoloplusParser {
    val color = Color.of(2607103)

    private val auth = HoloplusAuthorization()

    val talents = mutableMapOf<String, HoloplusTalent>()

    private inline fun <reified R : Any> get(path: String): Result<R, TrackerErr> {
        val request = newRequestBuilder()
            .get()
            .url("https://api.holoplus.com/$path")
        return request<R>(request)
    }

    private inline fun <reified R : Any> request(requestBuilder: Request.Builder): Result<R, TrackerErr> {
        return try {
            val request = requestBuilder
                .header("Authorization", auth.accessToken())
                .header("accept-language", "en")
                .header("content-type", "text/plain; charset=utf-8")
                .build()

            val response = OkHTTP.newCall(request).execute()
            response.use { rs ->
                if (!rs.isSuccessful) {
                    LOG.error("Error accessing Holoplus: ${request.url.encodedPath} :: ${rs.body.string()}")
                    Err(TrackerErr.IO)
                } else {
                    val body = rs.body.string()
                    try {
                        val json = MOSHI.adapter(R::class.java).fromJson(body)!!
                        Ok(json)
                    } catch (e: Exception) {
                        LOG.error("Unknown JSON provided from Holoplus: ${e.message} :: $body")
                        Err(TrackerErr.IO)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("HoloplusParser IO error: ${e.message}")
            LOG.debug(e.stackTraceString)
            return Err(TrackerErr.IO)
        }
    }

    fun getGens(groupId: String) = get<HoloplusGroup>("v2/groups/$groupId")

    fun getMembers(genId: String) = get<HoloplusGen>("v2/units/$genId")

    fun getFeed() = get<HoloplusFeed>("v4/talent-channel/channels")

    fun getTalentChannel(talentId: String) = get<HoloplusChannel>("v4/talent-channel/threads/newest?channel_id=$talentId&limit=20")

    fun cacheAllTalents() {
        val holoGens = getGens(HoloplusGroup.HOLOLIVE).orNull()?.units.orEmpty()
        val allTalents = holoGens.flatMap { gen ->
            getMembers(gen.id).orNull()?.talents.orEmpty()
        }.filterNot { talent ->
            // [Alum], [Retirement], [Affiliate]
            talent.name.contains("[")
        }.onEach { talent ->
            GlobalTrackSuggestionGenerator.cacheNewFeed(HoloplusTarget, talent.id, talent.name)
            LOG.info("Cached ${talent.id} = ${talent.name} :: $talent")
        }.map { talent ->
            talent.id to talent
        }
        talents.putAll(allTalents)
    }
}
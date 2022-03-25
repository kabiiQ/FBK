package moe.kabii.trackers.ps2.polling

import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.data.flat.Keys
import moe.kabii.newRequestBuilder
import moe.kabii.trackers.ps2.polling.json.*
import moe.kabii.util.extensions.stackTraceString
import okhttp3.OkHttpClient
import org.apache.commons.text.StringEscapeUtils
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.math.max

object PS2Parser {
    private val limitRate = Duration.ofMillis(500L)
    private var nextCall = Instant.now()

    private val httpClient
        = OkHttpClient
            .Builder()
            .readTimeout(Duration.ofSeconds(30))
            .connectTimeout(Duration.ofSeconds(30))
            .build()

    val iconRoot = "https://census.daybreakgames.com/files/ps2/images/static"
    private val serviceId = Keys.config[Keys.Planetside.censusId]

    private suspend inline fun <reified R: Any> censusRequest(substr: String): R {
        val delay = Duration.between(Instant.now(), nextCall).toMillis()
        delay(max(delay, 0L))

        val request = newRequestBuilder()
            .get()
            .url("http://census.daybreakgames.com/s:$serviceId/get/ps2:v2/$substr")
            .build()
        try {

            nextCall = Instant.now() + limitRate
            val response = httpClient.newCall(request).execute()
            try {
                val body = response.body!!.string()
                if(response.isSuccessful) {

                    val json = MOSHI.adapter(R::class.java).fromJson(body)
                    return json ?: throw IOException("Invalid JSON provided by Census: $body")

                } else throw IOException("PS2 Census returned error code: ${response.code}. Body :: $body")
            } finally {
                response.close()
            }
        } catch(e: Exception) {
            LOG.warn("PS2Parser: Error while calling census API: ${e.message}")
            LOG.debug(e.stackTraceString)
            throw e
        }
    }

    private const val outfitRequestResolve = "c:resolve=leader,leader_name,member_character_name,member_online_status"
    private const val playerRequestResolve = "c:resolve=online_status,profile,faction,world,outfit"
    private const val playerRequestJoin = "c:join=world^inject_at:world"

    private const val playerRequest = "$playerRequestResolve&$playerRequestJoin"
    private const val outfitRequest = outfitRequestResolve

    suspend fun searchOutfitByTag(tag: String): PS2Outfit?
        = censusRequest<PS2OutfitResponse>("outfit?alias_lower=${StringEscapeUtils.escapeHtml4(tag.lowercase())}&$outfitRequest").outfitList.firstOrNull()

    suspend fun searchOutfitByName(name: String): PS2Outfit?
        = censusRequest<PS2OutfitResponse>("outfit?name_lower=${StringEscapeUtils.escapeHtml4(name.lowercase())}&$outfitRequest").outfitList.firstOrNull()

    suspend fun searchOutfitById(id: String): PS2Outfit?
        = censusRequest<PS2OutfitResponse>("outfit?outfit_id=$id&$outfitRequest").outfitList.firstOrNull()

    suspend fun searchPlayerByName(name: String): PS2Player?
        = censusRequest<PS2PlayerResponse>("character?name.first_lower=${StringEscapeUtils.escapeHtml4(name.lowercase())}&$playerRequest").characters.firstOrNull()

    suspend fun searchPlayerById(id: String): PS2Player?
        = censusRequest<PS2PlayerResponse>("character?character_id=$id&$playerRequest").characters.firstOrNull()

    suspend fun getServers(): List<PS2Server>
        = censusRequest<PS2ServerResponse>("world?c:limit=20").worlds

    suspend fun searchServerByName(name: String): PS2Server?
        = getServers().find { server -> server.name.lowercase() == name }

    suspend fun getZones(): List<PS2Zone>
        = censusRequest<PS2ZoneResponse>("zone?c:limit=20").zoneList

    suspend fun getFacilities(): List<PS2Facility>
        = censusRequest<PS2FacilityResponse>("map_region?c:limit=1000").mapRegions
}

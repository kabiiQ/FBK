package moe.kabii.ps2

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.Keys
import moe.kabii.ps2.json.*
import moe.kabii.structure.extensions.stackTraceString
import okhttp3.Request
import org.apache.commons.lang.StringEscapeUtils
import java.io.IOException

object PS2Parser {
    val iconRoot = "https://census.daybreakgames.com/files/ps2/images/static"
    private val serviceId = Keys.config[Keys.Planetside.censusId]

    private inline fun <reified R: Any> censusRequest(substr: String): R {
        val request = Request.Builder()
            .get()
            .url("http://census.daybreakgames.com/s:FBK/get/ps2:v2/$substr")
            .header("User-Agent", "srkmfbk/1.0")
            .build()
        try {
            val response = OkHTTP.newCall(request).execute()
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

    fun searchOutfitByTag(tag: String): PS2Outfit?
        = censusRequest<PS2OutfitResponse>("outfit?alias_lower=${StringEscapeUtils.escapeHtml(tag.toLowerCase())}&$outfitRequestResolve").outfitList.firstOrNull()

    fun searchOutfitByName(name: String): PS2Outfit?
        = censusRequest<PS2OutfitResponse>("outfit?name_lower=${StringEscapeUtils.escapeHtml(name.toLowerCase())}&$outfitRequestResolve").outfitList.firstOrNull()

    fun searchPlayerByName(name: String): PS2Player?
        = censusRequest<PS2PlayerResponse>("character?name.first_lower=${StringEscapeUtils.escapeHtml(name.toLowerCase())}&$playerRequestResolve&$playerRequestJoin").characters.firstOrNull()

    fun getServers(): List<PS2Server>
        = censusRequest<PS2ServerResponse>("world?c:limit=20").worlds
}

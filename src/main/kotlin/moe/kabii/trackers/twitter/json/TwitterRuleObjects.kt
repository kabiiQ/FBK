package moe.kabii.trackers.twitter.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.JSON
import moe.kabii.MOSHI
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
data class TwitterRuleRequest private constructor(
    val add: List<TwitterRuleAddRequest>? = null,
    val delete: TwitterRuleDeleteRequest? = null
) {
    fun toRequestBody(): RequestBody = bodyAdapter.toJson(this).toRequestBody(JSON)

    companion object {
        private val bodyAdapter = MOSHI.adapter(TwitterRuleRequest::class.java)
        fun add(rules: List<String>) = TwitterRuleRequest(add = rules.map { rule -> TwitterRuleAddRequest(value = rule) })
        fun add(rule: String) = add(listOf(rule))
        fun delete(ids: List<Long>) = TwitterRuleRequest(delete = TwitterRuleDeleteRequest(ids.map { id -> id.toString() }))
        fun delete(id: Long) = delete(listOf(id))
    }
}

@JsonClass(generateAdapter = true)
data class TwitterRuleAddRequest(
    val tag: String? = null,
    val value: String
)

@JsonClass(generateAdapter = true)
data class TwitterRuleDeleteRequest(
    val ids: List<String>
)

@JsonClass(generateAdapter = true)
data class TwitterRuleResponse(
    val meta: TwitterRuleMetaResponse,
    val data: List<TwitterRuleDataResponse>?
) : TwitterResponse

@JsonClass(generateAdapter = true)
data class TwitterRuleMetaResponse(
    val summary: TwitterRuleSummary
)

@JsonClass(generateAdapter = true)
data class TwitterRuleSummary(
    val created: Int?,
    @Json(name = "not_created") val notCreated: Int?,
    val deleted: Int?,
    @Json(name = "not_deleted") val notDeleted: Int?
)

@JsonClass(generateAdapter = true)
data class TwitterRuleDataResponse(
    val value: String,
    @Json(name = "id") val ruleId: String
)
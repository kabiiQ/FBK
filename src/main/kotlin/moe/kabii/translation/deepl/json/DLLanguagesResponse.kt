package moe.kabii.translation.deepl.json

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import moe.kabii.MOSHI
import java.io.IOException

@JsonClass(generateAdapter = true)
data class DeepLSupportedLanguage(
    val language: String,
    val name: String
) {
    companion object {
        @Throws(IOException::class)
        fun parseList(json: String): List<DeepLSupportedLanguage>? {
            val type = Types.newParameterizedType(List::class.java, DeepLSupportedLanguage::class.java)
            val adapter = MOSHI.adapter<List<DeepLSupportedLanguage>>(type)
            return adapter.fromJson(json)
        }
    }
}

package moe.kabii.translation.argos.json

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import moe.kabii.MOSHI
import java.io.IOException

data class ArgosLanguagesResponse(
    val languages: List<ArgosSupportedLanguage>
) {
    companion object {
        @Throws(IOException::class)
        fun parseLanguages(json: String): List<ArgosSupportedLanguage> {
            val type = Types.newParameterizedType(List::class.java, ArgosSupportedLanguage::class.java)
            val adapter = MOSHI.adapter<List<ArgosSupportedLanguage>>(type)
            return adapter.fromJson(json)!!
        }
    }
}

@JsonClass(generateAdapter = true)
data class ArgosSupportedLanguage(
    val code: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class ArgosTranslationError(
    val error: String
)
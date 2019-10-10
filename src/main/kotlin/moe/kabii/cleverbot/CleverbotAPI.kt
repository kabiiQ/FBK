package moe.kabii.cleverbot

internal object CleverbotAPI {
    data class SessionRequest(
        val user: String,
        val key: String
    )

    data class SessionResponse(
        val status: String,
        val nick: String
    )

    data class QueryRequest(
        val user: String,
        val key: String,
        val nick: String,
        val text: String
    )

    data class QueryResponse(
        val status: String,
        val response: String
    )
}
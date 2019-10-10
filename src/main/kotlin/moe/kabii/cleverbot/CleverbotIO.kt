package moe.kabii.cleverbot

object CleverbotAPIErr

// cleverbot.io api
/* object CleverbotIO {
    private val auth = Keys.CLEVERBOT
    private val klaxon = Klaxon()

    fun newSession(): Result<String, CleverbotAPIErr> {
        val create = CleverbotAPI.SessionRequest(auth.user, auth.key)
        val body = klaxon.toJsonString(create).toRequestBody()
        val request = Request.Builder()
            .post(body)
            .url("https://cleverbot.io/1.0/create")
        val response: Result<String?, Throwable> = OkHTTP.make(request) { response ->
            if(response.isSuccessful) {
                val body = response.body!!.string()
                val json = klaxon.parse<CleverbotAPI.SessionResponse>(body)
                if(json != null) return@make json.nick
            }
            null
        }
        if(response is Ok) {
            val nick = response.value
            if(nick != null) return Ok(nick)
        }
        return Err(CleverbotAPIErr)
    }

    fun query(session: String, query: String): Result<String, CleverbotAPIErr> {
        val ask = CleverbotAPI.QueryRequest(auth.user, auth.key, session, query)
        val body = klaxon.toJsonString(ask).toRequestBody()
        val request = Request.Builder()
            .post(body)
            .url("https://cleverbot.io/1.0/ask")
        val response: Result<String?, Throwable> = OkHTTP.make(request) { response ->
            if(response.isSuccessful) {
                val body = response.body!!.string()
                val json = klaxon.parse<CleverbotAPI.QueryResponse>(body)
                if(json != null) return@make json.response
            }
            null
        }
        if(response is Ok) {
            val cbResponse = response.value
            if(cbResponse != null) return Ok(cbResponse)
        }
        return Err(CleverbotAPIErr)
    }
} */
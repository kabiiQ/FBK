package moe.kabii.net.oauth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.net.oauth.discord.DiscordAuthorization
import moe.kabii.newRequestBuilder
import moe.kabii.util.extensions.log
import moe.kabii.util.extensions.stackTraceString
import okhttp3.FormBody
import java.io.File
import java.util.concurrent.Executors

enum class AuthStage {
    AWAIT_REDIRECT,
    AWAIT_TOKEN,
    COMPLETE
}

open class OAuthProcess(
    val discordUser: Long,
    val authUrl: String,
    var process: AuthStage = AuthStage.AWAIT_REDIRECT,
    var authCode: String? = null,
    var accessToken: String? = null,
    val tokenCallback: suspend (OAuthProcess) -> Unit
) {
    fun code(code: String) {
        this.authCode = code
        this.process = AuthStage.AWAIT_TOKEN
    }
    fun token(token: String) {
        this.accessToken = token
        this.process = AuthStage.COMPLETE
    }
}

@JsonClass(generateAdapter = true)
data class OAuthTokenResponse(
    @Json(name = "access_token") val token: String
) {
    companion object {
        val adapter = MOSHI.adapter(OAuthTokenResponse::class.java)
        fun fromJson(json: String) = adapter.fromJson(json)
    }
}

abstract class OAuthRedirectServer(val service: String, serverIndex: Int) {

    abstract val authorizationUrl: String
    abstract val tokenUrl: String
    abstract val oauthStates: MutableMap<String, OAuthProcess>

    abstract val webResponse: File

    private val port = 8100 + serverIndex
    val address = "${Keys.config[Keys.OAuth.rootOauthUri]}:$port"

    private val apiThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val apiScope = CoroutineScope(apiThread + CoroutineName("OAuth-Redirect-$port") + SupervisorJob())

    init {
        LOG.info("Netty oauth redirect binding to port $port. Service:$service")
    }

    val server = embeddedServer(Netty, port = port) {
        routing {
            get {
                log("GET:$port")

                val params = call.request.queryParameters
                val state = params["state"] ?: return@get
                val code = params["code"] ?: return@get

                // validate redirect
                val process = oauthStates[state]
                if(process == null) {
                    LOG.warn("OAuth redirected from unknown state: $state")
                    call.respondText("This authorization URL is invalid or expired. Please re-run the /ytlink command in Discord for a new URL.", status = HttpStatusCode.Unauthorized)
                    return@get
                }
                // received valid code
                process.code(code)

                call.response.status(HttpStatusCode.OK)
                call.respondFile(webResponse)
                // exchange access code for user token
                apiScope.launch {
                    val formBody = FormBody.Builder()
                        .add("grant_type", "authorization_code")
                        .add("code", code)
                        .add("redirect_uri", address)
                        .add("client_id", DiscordAuthorization.discordClientId)
                        .add("client_secret", DiscordAuthorization.discordClientSecret)
                        .build()

                    val request = newRequestBuilder()
                        .url(tokenUrl)
                        .post(formBody)
                        .build()
                    val response = OkHTTP.newCall(request).execute()
                    response.use { rs ->
                        LOG.debug("Requesting OAuth $service token: ${rs.code}")
                        if(response.isSuccessful) {
                            try {
                                val body = rs.body.string()
                                val token = OAuthTokenResponse.fromJson(body)!!
                                process.token(token.token)
                                process.tokenCallback(process)
                                oauthStates.remove(state)
                            } catch(e: Exception) {
                                LOG.error("Unable to parse OAuth $service token response: ${e.message} :: $rs")
                                LOG.warn(e.stackTraceString)
                            }
                        }
                    }
                }
            }
        }
    }
}
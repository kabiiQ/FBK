package moe.kabii.net.oauth.discord

import discord4j.common.util.Snowflake
import moe.kabii.data.flat.Keys
import moe.kabii.net.oauth.OAuthProcess
import moe.kabii.net.oauth.OAuthRedirectServer
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import java.io.File

object DiscordAuthorization {

    val discordClientId = Keys.config[Keys.DiscordOAuth.clientId]
    val discordClientSecret = Keys.config[Keys.DiscordOAuth.clientSecret]
    private val signingKey = Keys.config[Keys.OAuth.stateKey]

    val server = DiscordOAuthRedirectServer

    enum class DiscordScopes(val scope: String) {
        CONNECTIONS("connections")
    }

    fun createNew(privateId: Snowflake, user: Snowflake, vararg scopes: DiscordScopes, tokenCallback: suspend (OAuthProcess) -> Unit): OAuthProcess {
        val state = HmacUtils(HmacAlgorithms.HMAC_SHA_256, signingKey).hmacHex(privateId.asString())

        val authUrl = StringBuilder(DiscordOAuthRedirectServer.authorizationUrl)
            .append("?response_type=code")
            .append("&client_id=").append(discordClientId)
            .append("&redirect_uri=").append(server.address)
            .append("&scope=").append(scopes.joinToString(" ", transform = DiscordScopes::scope))
            .append("&state=").append(state)

        val process = OAuthProcess(user.asLong(), authUrl.toString(), tokenCallback = tokenCallback)
        DiscordOAuthRedirectServer.oauthStates[state] = process
        return process
    }
}

object DiscordOAuthRedirectServer : OAuthRedirectServer("discord", 1) {
    override val authorizationUrl = "https://discord.com/api/oauth2/authorize"
    override val tokenUrl = "https://discord.com/api/oauth2/token"

    override val oauthStates: MutableMap<String, OAuthProcess> = mutableMapOf()

    override val webResponse = File("files/web", "discord_integration.html")
}
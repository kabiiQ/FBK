package moe.kabii.data.flat

/**
 * Flags for enabling/disabling services which may require external resources/API keys
 */
object AvailableServices {
    val discordOAuth = Keys.config[Keys.DiscordOAuth.clientSecret].isNotBlank()

    val youtubeApi = Keys.config[Keys.Youtube.keys].isNotEmpty()
    val youtubePubSub = Keys.config[Keys.Youtube.callbackAddress].isNotBlank()
    val youtube = youtubeApi && youtubePubSub

    val twitchApi = Keys.config[Keys.Twitch.secret].isNotBlank()
    val twitchWebhooks = Keys.config[Keys.Twitch.signingKey].isNotBlank()
    val twitch = twitchApi && twitchWebhooks

    val nitter = Keys.config[Keys.Nitter.instanceUrls].isNotEmpty()

    val mtl = Keys.config[Keys.Microsoft.translatorKey].isNotBlank()
    val gtl = Keys.config[Keys.Google.gTranslatorKey].isNotBlank()
    val deepL = Keys.config[Keys.DeepL.authKey].isNotBlank()

    val twitCastingApi = Keys.config[Keys.Twitcasting.clientSecret].isNotBlank()
    val twitCastingWebhooks = Keys.config[Keys.Twitcasting.signature].isNotBlank()
    val twitCasting = twitCastingApi && twitCastingWebhooks

    val wolfram = Keys.config[Keys.Wolfram.appId].isNotBlank()

    val mal = Keys.config[Keys.MAL.malKey].isNotBlank()
}
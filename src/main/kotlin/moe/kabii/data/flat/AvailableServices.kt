package moe.kabii.data.flat

/**
 * Flags for enabling/disabling services which may require external resources/API keys
 */
object AvailableServices {
    val youtubeApi = Keys.config[Keys.Youtube.keys].isNotEmpty()
    val youtubePubSub = Keys.config[Keys.Youtube.callbackAddress].isNotBlank()
    val youtubePoller = Keys.config[Keys.Youtube.backup]
    val youtube = youtubeApi && (youtubePubSub || youtubePoller)

    val twitchApi = Keys.config[Keys.Twitch.secret].isNotBlank()
    val twitchWebhooks = Keys.config[Keys.Twitch.callback].isNotBlank()
    val twitch = twitchApi && twitchWebhooks

    val nitter = Keys.config[Keys.Nitter.instanceUrls].isNotEmpty()
    val twitterWhitelist = Keys.config[Keys.Nitter.whitelist]

    val bluesky = Keys.config[Keys.Bluesky.password].isNotBlank()

    val mtl = Keys.config[Keys.Microsoft.translatorKey].isNotBlank()
    val gtl = Keys.config[Keys.Google.gTranslatorKey].isNotBlank()
    val deepL = Keys.config[Keys.DeepL.authKey].isNotBlank()

    val twitCastingApi = Keys.config[Keys.Twitcasting.clientSecret].isNotBlank()
    val twitCastingWebhooks = Keys.config[Keys.Twitcasting.signature].isNotBlank()
    val twitCasting = twitCastingApi && twitCastingWebhooks

    val kickApi = Keys.config[Keys.Kick.clientSecret].isNotBlank()
    val kickWebhooks = Keys.config[Keys.Kick.subscriptions]

    val wolfram = Keys.config[Keys.Wolfram.appId].isNotBlank()

    val mal = Keys.config[Keys.MAL.malKey].isNotBlank()
    val aniList = Keys.config[Keys.AniList.enabled]

    val discordOAuth = Keys.config[Keys.OAuth.clientSecret].isNotBlank()
            && Keys.config[Keys.OAuth.rootOauthUri].isNotBlank()

    val ytVideosServer = Keys.config[Keys.API.ytVideos]
    val externalCommandsServer = Keys.config[Keys.API.externalCommands]
}
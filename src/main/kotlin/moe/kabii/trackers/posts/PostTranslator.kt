package moe.kabii.trackers.posts

import moe.kabii.LOG
import moe.kabii.data.mongodb.guilds.PostsSettings
import moe.kabii.data.mongodb.guilds.TranslatorSettings
import moe.kabii.trackers.posts.PostWatcher.TrackedSocialTarget
import moe.kabii.trackers.posts.twitter.TwitFixParser
import moe.kabii.translation.*
import moe.kabii.translation.argos.ArgosTranslator
import moe.kabii.translation.google.GoogleTranslator
import moe.kabii.util.extensions.stackTraceString

object PostTranslator {

    object TwitterTranslator : TranslationService(
        "Grok",
        ""
    ) {
        override val supportedLanguages: SupportedLanguages = SupportedLanguages(this, mapOf())

        override fun doTranslation(from: TranslationLanguage?, to: TranslationLanguage, rawText: String, apiKey: String?):TranslationResult =
            error("Generic translation from Twitter service")
    }

    fun translatePost(text: String, repost: Boolean, feedName: String, postTargets: List<TrackedSocialTarget>, tlSettings: TranslatorSettings, postSettings: PostsSettings, cache: MutableMap<String, TranslationResult>, tweetId: Long? = null): TranslationResult? {
        return if(postSettings.autoTranslate && text.isNotBlank()) {
            try {
                val lang = tlSettings.defaultTargetLanguage
                val translator = Translator.getService(text, listOf(lang), feedName = feedName, primaryTweet = !repost, guilds = postTargets.mapNotNull(TrackedSocialTarget::discordGuild))

                // check cache for existing translation of this tweet
                val standardLangTag = Translator.baseService.supportedLanguages[lang]?.tag ?: lang
                val existingTl = cache[standardLangTag]
                val translation = if(existingTl != null && (existingTl.service == GoogleTranslator || translator.service != GoogleTranslator)) existingTl else {

                    // Override translator selection for specific tweet solution
                    val twitterTl = if(tweetId != null) {
                        TwitFixParser.getTweetInfo(tweetId, standardLangTag)?.tweet?.translation?.run {
                            val source = TranslationLanguage(source, sourceFull, sourceFull)
                            val target = TranslationLanguage(target, target, target)
                            TranslationResult(TwitterTranslator, source, target, this@run.text, detected = true)
                        }
                    } else null

                    val tl = twitterTl ?: translator.translate(from = null, to = translator.getLanguage(lang), text = text)
                    cache[standardLangTag] = tl
                    tl
                }

                if(tlSettings.skipRetweets && translation.service == ArgosTranslator) return null
                if(translation.originalLanguage != translation.targetLanguage && translation.translatedText.isNotBlank()) translation
                else null

            } catch(e: Exception) {
                LOG.warn("Tweet translation failed: ${e.message} :: ${e.stackTraceString}")
                null
            }
        } else null
    }
}
package moe.kabii.trackers.posts

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.PostsSettings
import moe.kabii.data.mongodb.guilds.TranslatorSettings
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.instances.DiscordInstances
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.translation.TranslationResult
import moe.kabii.translation.Translator
import moe.kabii.translation.google.GoogleTranslator
import moe.kabii.util.extensions.*
import reactor.kotlin.core.publisher.toMono
import kotlin.reflect.KProperty1

abstract class PostWatcher(val instances: DiscordInstances) {

    protected val taskScope = CoroutineScope(DiscordTaskPool.socialThreads + CoroutineName("PostWatcher") + SupervisorJob())
    protected val notifyScope = CoroutineScope(DiscordTaskPool.notifyThreads + CoroutineName("PostWatcher-Notify") + SupervisorJob())

    /**
     * Object to hold information about a tracked target from the database - resolving references to reduce transactions later
     */
    data class TrackedSocialTarget(
        val db: Int,
        val discordClient: Int,
        val dbFeed: Int,
        val username: String,
        val discordChannel: Snowflake,
        val discordGuild: Snowflake?,
        val discordUser: Snowflake
    ) {
        @RequiresExposedContext fun findDbTarget() = TrackedSocialFeeds.SocialTarget.findById(db)!!
    }

    @RequiresExposedContext
    suspend fun loadTarget(target: TrackedSocialFeeds.SocialTarget) = with(target.socialFeed.feedInfo()) {
        TrackedSocialTarget(
            target.id.value,
            target.discordClient,
            target.socialFeed.id.value,
            displayName,
            target.discordChannel.channelID.snowflake,
            target.discordChannel.guild?.guildID?.snowflake,
            target.tracker.userID.snowflake
        )
    }

    fun <T> discordTask(timeoutMillis: Long = 6_000L, block: suspend() -> T) = taskScope.launch {
        withTimeout(timeoutMillis) {
            block()
        }
    }

    @CreatesExposedContext
    suspend fun getActiveTargets(feed: TrackedSocialFeeds.SocialFeed): List<TrackedSocialTarget>? {
        val targets = propagateTransaction {
            feed.targets.map { t -> loadTarget(t) }
        }
        val existingTargets = targets
            .filter { target ->
                val discord = instances[target.discordClient].client
                // untrack target if discord channel is deleted
                if (target.discordGuild != null) {
                    try {
                        discord.getChannelById(target.discordChannel).awaitSingle()
                    } catch (e: Exception) {
                        if(e is ClientException) {
                            if(e.status.code() == 401) return emptyList()
                            if(e.status.code() == 404) {
                                propagateTransaction {
                                    val feedInfo = feed.feedInfo()
                                    LOG.info("Untracking ${feedInfo.site.full} feed '${feedInfo.displayName}' in ${target.discordChannel} as the channel seems to be deleted.")
                                    target.findDbTarget().delete()
                                }
                            }
                        }
                        return@filter false
                    }
                }
                true
            }
        return if (existingTargets.isNotEmpty()) {
            existingTargets.filter { target ->
                // ignore, but do not untrack targets with feature disabled
                val clientId = instances[target.discordClient].clientId
                val guildId = target.discordGuild?.asLong() ?: return@filter true // DM do not have channel features
                val featureChannel = GuildConfigurations.getOrCreateGuild(clientId, guildId).getOrCreateFeatures(target.discordChannel.asLong())
                featureChannel.postsTargetChannel
            }
        } else {
            propagateTransaction {
                val feedInfo = feed.feedInfo()
                LOG.info("${feedInfo.site.full} feed ${feedInfo.displayName} returned NO active targets.")

                if(feed.site != TrackedSocialFeeds.DBSite.X) { // do not auto-untrack X feeds as they are manually whitelisted anyways
                    feed.delete()
                    LOG.info("Untracking ${feedInfo.site.full} feed ${feedInfo.displayName} as it has no targets.")
                }
            }
            null
        }
    }

    data class SocialMentionRole(val db: TrackedSocialFeeds.SocialTargetMention, val discord: Role?) {

        fun toText(includeRole: Boolean): String {
            val rolePart = if(discord == null || !includeRole) null
            else discord.mention.plus(" ")
            val textPart = db.mentionText?.plus(" ")
            return "${rolePart ?: ""}${textPart ?: ""}"
        }
    }


    @CreatesExposedContext
    suspend fun getMentionRoleFor(dbTarget: TrackedSocialTarget, targetChannel: MessageChannel, postCfg: PostsSettings, mentionOption: KProperty1<PostsSettings, Boolean>): SocialMentionRole? {
        // do not return ping if not configured for channel/tweet type
        if(!postCfg.mentionRoles) return null
        if(!mentionOption(postCfg)) return null

        val dbMentionRole = propagateTransaction {
            dbTarget.findDbTarget().mention()
        } ?: return null
        val dRole = if(dbMentionRole.mentionRole != null) {
            targetChannel.toMono()
                .ofType(GuildChannel::class.java)
                .flatMap(GuildChannel::getGuild)
                .flatMap { guild -> guild.getRoleById(dbMentionRole.mentionRole!!.snowflake) }
                .tryAwait()
        } else null
        val discordRole = when(dRole) {
            is Ok -> dRole.value
            is Err -> {
                val err = dRole.value
                if(err is ClientException && err.status.code() == 404) {
                    // role has been deleted, remove configuration
                    propagateTransaction {
                        if (dbMentionRole.mentionText != null) {
                            // don't delete if mentionrole still has text component
                            dbMentionRole.mentionRole = null
                        } else {
                            dbMentionRole.delete()
                        }
                    }
                }
                null
            }
            null -> null
        }
        return SocialMentionRole(dbMentionRole, discordRole)
    }

    fun translatePost(text: String, repost: Boolean, feedName: String, postTargets: List<TrackedSocialTarget>, tlSettings: TranslatorSettings, postSettings: PostsSettings, cache: MutableMap<String, TranslationResult>): TranslationResult? {
        return if(postSettings.autoTranslate && text.isNotBlank()) {
            try {
                // Retweets default to low-quality local translations. If "skipRetweets" is set by user, retweets should just forego translation.
                if(!repost || !tlSettings.skipRetweets) {

                    val lang = tlSettings.defaultTargetLanguage
                    val translator = Translator.getService(text, listOf(lang), feedName = feedName, primaryTweet = !repost, guilds = postTargets.mapNotNull(TrackedSocialTarget::discordGuild))

                    // check cache for existing translation of this tweet
                    val standardLangTag = Translator.baseService.supportedLanguages[lang]?.tag ?: lang
                    val existingTl = cache[standardLangTag]
                    val translation = if(existingTl != null && (existingTl.service == GoogleTranslator || translator.service != GoogleTranslator)) existingTl else {

                        val tl = translator.translate(from = null, to = translator.getLanguage(lang), text = text)
                        cache[standardLangTag] = tl
                        tl
                    }

                    if(translation.originalLanguage != translation.targetLanguage && translation.translatedText.isNotBlank()) translation
                    else null
                } else null

            } catch(e: Exception) {
                LOG.warn("Tweet translation failed: ${e.message} :: ${e.stackTraceString}")
                null
            }
        } else null
    }
}
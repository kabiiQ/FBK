package moe.kabii.trackers.twitter.watcher

import discord4j.common.util.Snowflake
import discord4j.common.util.TimestampFormat
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.MessageCreateFields
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.TwitterFeedCache
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.TwitterSettings
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterMention
import moe.kabii.data.relational.twitter.TwitterTarget
import moe.kabii.discord.util.Embeds
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.twitter.TwitterDateTimeUpdateException
import moe.kabii.trackers.twitter.TwitterParser
import moe.kabii.trackers.twitter.TwitterRateLimitReachedException
import moe.kabii.trackers.twitter.json.TwitterMediaType
import moe.kabii.trackers.twitter.json.TwitterTweet
import moe.kabii.trackers.twitter.json.TwitterUser
import moe.kabii.trackers.videos.spaces.watcher.SpaceChecker
import moe.kabii.translation.TranslationLanguage
import moe.kabii.translation.TranslationResult
import moe.kabii.translation.Translator
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.*
import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.StringEscapeUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.kotlin.core.publisher.toMono
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class TwitterChecker(val discord: GatewayDiscordClient, val cooldowns: ServiceRequestCooldownSpec, val spaceChecker: SpaceChecker) : Runnable {

    override fun run() {
        applicationLoop {
            val start = Instant.now()

            newSuspendedTransaction {
                try {
                    LOG.debug("TwitterChecker :: start: $start")
                    // get all tracked twitter feeds
                    val feeds = TwitterFeed.all()
                    LOG.debug("2")

                        // feeds who are completely inactive and since_id has fallen out of the valid range
                    val requireUpdate = mutableListOf<TwitterFeed>()
                    var maxId = 0L

                    var first = true
                    feeds.forEach { feed ->
                        LOG.debug("${feed.lastKnownUsername}: start")
                        if(!first) {
                            delay(Duration.ofMillis(cooldowns.callDelay))
                        } else first = false

                        val targets = getActiveTargets(feed)?.ifEmpty { null }
                            ?: return@forEach // feed untrack entirely or no target channels are currently enabled
                        LOG.debug("targets: returned")

                        val cache = TwitterFeedCache.getOrPut(feed)

                        // determine if any targets want RT or quote tweets
                        var pullRetweets = false
                        var pullQuotes = false

                        targets.forEach { target ->
                            val features = GuildConfigurations.findFeatures(target)
                            val twitter = features?.twitterSettings ?: TwitterSettings()

                            if(twitter.displayRetweet) pullRetweets = true
                            if(twitter.displayQuote) pullQuotes = true
                        }
                        LOG.debug("target iter: return")

                        val limits = TwitterParser.TwitterQueryLimits(
                            sinceId = feed.lastPulledTweet,
                            includeRT = pullRetweets,
                            includeQuote = pullQuotes
                        )
                        LOG.debug("getting tweets: start")
                        val recent = try {
                            TwitterParser.getRecentTweets(feed.userId, limits)
                        } catch(sinceId: TwitterDateTimeUpdateException) {
                            LOG.info("Twitter feed '${feed.userId}' is far out of date and the Tweets since_id query was rejected")
                            requireUpdate.add(feed)
                            null
                        } catch(rate: TwitterRateLimitReachedException) {
                            val reset = rate.reset
                            LOG.warn("Twitter rate limit reached: sleeping ${reset.seconds} seconds")
                            delay(reset)
                            null
                        } catch(e: Exception) {
                            LOG.warn("TwitterChecker: Error in Twitter call: ${e.message}")
                            LOG.debug(e.stackTraceString)
                            delay(Duration.ofMillis(100L))
                            null
                        }
                        LOG.debug("tweets returned")
                        recent ?: return@forEach
                        val (user, tweets) = recent
                        feed.lastKnownUsername = user.username

                        val latest = tweets.maxOf { tweet ->
                            // if tweet is after last posted tweet and within 2 hours (arbitrary - to prevent spam when initially tracking) - send discord notifs
                            val age = Duration.between(tweet.createdAt, Instant.now())

                            // if already handled or too old, skip, but do not pull tweet ID again
                            if((feed.lastPulledTweet ?: 0) >= tweet.id
                                || age > Duration.ofHours(2)
                                || cache.seenTweets.contains(tweet.id)
                            ) return@maxOf tweet.id

                            notifyTweet(user, tweet, targets)
                        }
                        if(latest > (feed.lastPulledTweet ?: 0L)) {
                            transaction {
                                feed.lastPulledTweet = latest
                            }
                        }
                        if(latest > maxId) maxId = latest
                        LOG.debug("latest tweets: checked")
                    }

                    requireUpdate.forEach { feed ->
                        transaction {
                            feed.lastPulledTweet = maxId
                        }
                    }
                    LOG.debug("twitter exit")
                } catch(e: Exception) {
                    LOG.info("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }
            val runDuration = Duration.between(start, Instant.now())
            val delay = cooldowns.minimumRepeatTime - runDuration.toMillis()
            delay(Duration.ofMillis(max(delay, 0L)))
        }
    }

    @WithinExposedContext
    suspend fun notifyTweet(user: TwitterUser, tweet: TwitterTweet, targets: List<TwitterTarget>): Long {
        LOG.debug("notify ${user.username} tweet: $tweet :: begin")
        // send discord notifs - check if any channels request
        TwitterFeedCache[user.id]?.seenTweets?.add(tweet.id)

        // check for twitter space info from tweet
        //spaceChecker.intakeSpaceFromTweet(tweet)

        // cache to not repeat translation for same tweet across multiple channels/servers
        val translations = mutableMapOf<TranslationLanguage, TranslationResult>()

        targets.forEach target@{ target ->
            try {
                // post a notif to this target
                val channel = discord.getChannelById(target.discordChannel.channelID.snowflake)
                    .ofType(MessageChannel::class.java)
                    .awaitSingle()

                val features = GuildConfigurations.findFeatures(target)
                val twitter = features?.twitterSettings ?: TwitterSettings()

                if(!tweet.notifyOption.get(twitter)) return@target

                val referenceUser = tweet.references.firstOrNull()?.author
                val action = when {
                    tweet.retweet -> "retweeted \uD83D\uDD01"
                    tweet.reply -> "replied to a Tweet from **@${referenceUser?.username}** \uD83D\uDCAC"
                    tweet.quote -> "quoted a Tweet from **@${referenceUser?.username}** \uD83D\uDDE8"
                    else -> "posted a new Tweet"
                }

                if(tweet.sensitive == true && target.discordChannel.guild != null) {
                    // filter potentially nsfw tweets in guilds
                    val guildChan = channel as? TextChannel // will fail for news channels as they can not be marked nsfw
                    if(guildChan?.isNsfw != true) {
                        channel.createMessage(
                            Embeds.fbk("[**@${user.username}**](${user.url}) $action which may contain sensitive content.")
                        ).awaitSingle()
                        return@target
                    }
                }

                LOG.debug("translation stage")
                val translation = if(twitter.autoTranslate && tweet.text.isNotBlank()) {
                    try {
                        val baseService = Translator.defaultService
                        val defaultLang = GuildConfigurations
                            .getOrCreateGuild(target.discordChannel.guild!!.guildID)
                            .translator.defaultTargetLanguage
                            .run(baseService.supportedLanguages::get) ?: baseService.defaultLanguage()
                        val translator = Translator.getService(tweet.text, defaultLang.tag)

                        // check cache for existing translation of this tweet
                        val existingTl = translations[defaultLang]
                        val translation = if(existingTl != null) existingTl else {

                            val tl = translator.translate(from = null, to = defaultLang, text = tweet.text)
                            translations[tl.targetLanguage] = tl
                            tl
                        }

                        if(translation.originalLanguage != translation.targetLanguage && translation.translatedText.isNotBlank()) translation
                        else null

                    } catch(e: Exception) {
                        LOG.warn("Tweet translation failed: ${e.message} :: ${e.stackTraceString}")
                        null
                    }
                } else null

                var editedThumb: ByteArrayInputStream? = null
                var attachedVideo: String? = null
                val attachment = tweet.attachments.firstOrNull()
                val attachType = attachment?.type
                val size = tweet.attachments.size
                var attachInfo = ""

                when {
                    attachType == TwitterMediaType.VID || attachType == TwitterMediaType.GIF -> {
                        attachInfo = "(Open on Twitter to view video)\n"
                        // process video attachment
                        attachedVideo = try {
                            TwitterParser.getV1Tweet(tweet.id.toString())?.findAttachedVideo()
                        } catch(e: Exception) {
                            LOG.warn("Error getting V1 Tweet from feed: ${tweet.id}")
                            null
                        }

                        if(attachedVideo == null) {
                            // if we can't provide video, revert to notifying user/regular thumbnail attachment
                            if(attachment.url != null) {
                                editedThumb = TwitterThumbnailGenerator.attachInfoTag(attachment.url, video = true)
                            }
                        }
                    }
                    size > 1 -> {
                        attachInfo = "(Open on Twitter to view $size images)\n"
                        // tag w/ number of photos
                        if(attachment?.url != null) {
                            editedThumb = TwitterThumbnailGenerator.attachInfoTag(attachment.url, imageCount = size)
                        }
                    }
                }

                // only use a static thumbnail if a video is not attached
                val thumbnail = if(attachedVideo == null) {
                    attachment?.url
                        ?: tweet.entities?.urls?.firstOrNull()?.images?.firstOrNull()?.url // fallback to image from embedded twitter link, if it exists (discord uses these in the vanilla Twitter embed)
                        ?: tweet.references.firstOrNull()?.attachments?.firstOrNull()?.url
                        ?: tweet.references.firstOrNull()?.entities?.urls?.firstOrNull()?.images?.firstOrNull()?.url
                } else null

                LOG.debug("roles phase")
                // mention roles
                val guildId = target.discordChannel.guild?.guildID
                val mention = guildId
                    ?.run { getMentionRoleFor(target, this.snowflake, channel, tweet, twitter) }
                    ?.discord?.mention
                    ?.plus(" ")
                    ?: ""

                val notifSpec = MessageCreateSpec.create()
                    // todo channel setting for custom message ?
                    .run {
                        val timestamp = TimestampFormat.RELATIVE_TIME.format(tweet.createdAt)
                        withContent("$mention**@${user.username}** $action $timestamp: https://twitter.com/${user.username}/status/${tweet.id}")
                    }
                    .run {
                        if(editedThumb != null) withFiles(MessageCreateFields.File.of("thumbnail_edit.png", editedThumb)) else this
                    }
                    .run {
                        val footer = StringBuilder(attachInfo)

                        val color = if(user.id == 1255017971363090432L) 16703383 else 1942002
                        val author = (if(tweet.retweet) referenceUser else user) ?: user

                        val embed = Embeds.other(StringEscapeUtils.unescapeHtml4(tweet.text), Color.of(color))
                            .withAuthor(EmbedCreateFields.Author.of("${author.name} (@${author.username})", author.url, author.profileImage))
                            .run {
                                if(translation != null) {
                                    val tlText = StringUtils.abbreviate(StringEscapeUtils.unescapeHtml4(translation.translatedText), MagicNumbers.Embed.FIELD.VALUE)
                                    footer.append("Translator: ${translation.service.fullName}, ${translation.originalLanguage.tag} -> ${translation.targetLanguage.tag}\n")
                                    withFields(EmbedCreateFields.Field.of("**Tweet Translation**", tlText, false))
                                } else this
                            }
                            .run {
                                if(footer.isNotBlank()) withFooter(EmbedCreateFields.Footer.of(footer.toString(), NettyFileServer.twitterLogo)) else this
                            }
                            .run {
                                if(editedThumb != null) {
                                    // always use our edited thumbnail if we produced one for this
                                    withImage("attachment://thumbnail_edit.png")
                                } else if(thumbnail != null) withImage(thumbnail)
                                else this
                            }
                        withEmbeds(embed)
                    }

                val notif = if(twitter.mediaOnly && editedThumb == null && thumbnail == null) return@target else channel.createMessage(notifSpec).awaitSingle()

                if(attachedVideo != null) {
                    channel.createMessage(attachedVideo)
                        .withMessageReference(notif.id)
                        .tryAwait()
                }

                TrackerUtil.checkAndPublish(notif)
            } catch (e: Exception) {
                if (e is ClientException && e.status.code() == 403) {
                    TrackerUtil.permissionDenied(discord, target.discordChannel.guild?.guildID, target.discordChannel.channelID, FeatureChannel::twitterTargetChannel, target::delete)
                    LOG.warn("Unable to send Tweet to channel '${target.discordChannel.channelID}'. Disabling feature in channel. TwitterChecker.java")
                } else {
                    LOG.warn("Error sending Tweet to channel: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }
        }
        LOG.debug("$tweet :: complete??")
        return tweet.id // return tweet id for 'max' calculation to find the newest tweet that was returned
    }

    @WithinExposedContext
    suspend fun getActiveTargets(feed: TwitterFeed): List<TwitterTarget>? {
        val existingTargets = feed.targets.toList()
            .filter { target ->
                // untrack target if discord channel is deleted
                if (target.discordChannel.guild != null) {
                    try {
                        discord.getChannelById(target.discordChannel.channelID.snowflake).awaitSingle()
                    } catch (e: Exception) {
                        if (e is ClientException && e.status.code() == 404) {
                            LOG.info("Untracking Twitter feed '${feed.userId}' in ${target.discordChannel.channelID} as the channel seems to be deleted.")
                            target.delete()
                        }
                        return@filter false
                    }
                }
                true
            }
        return if (existingTargets.isNotEmpty()) {
            existingTargets.filter { target ->
                // ignore, but do not untrack targets with feature disabled
                GuildConfigurations.findFeatures(target)?.twitterTargetChannel != false // enabled or DM
            }
        } else {
            try {
                TwitterFeedSubscriber.removeStreamingFeeds(listOf(feed))
            } catch(e: Exception) {
                LOG.error("Error removing streaming feed subscription: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            feed.delete()
            LOG.info("Untracking Twitter feed ${feed.userId} as it has no targets.")
            null
        }
    }

    data class TwitterMentionRole(val db: TwitterMention, val discord: Role)
    @WithinExposedContext
    suspend fun getMentionRoleFor(dbTarget: TwitterTarget, guildId: Snowflake, targetChannel: MessageChannel, tweet: TwitterTweet, twitterCfg: TwitterSettings): TwitterMentionRole? {
        if(tweet.retweet) return null
        if(!twitterCfg.mentionRoles) return null
        val dbMentionRole = TwitterMention.getRoleFor(guildId, dbTarget.twitterFeed.userId)
            .firstOrNull() ?: return null
        val dRole = targetChannel.toMono()
            .ofType(GuildChannel::class.java)
            .flatMap(GuildChannel::getGuild)
            .flatMap { guild -> guild.getRoleById(dbMentionRole.mentionRole.snowflake) }
            .tryAwait()
        return when(dRole) {
            is Ok -> TwitterMentionRole(dbMentionRole, dRole.value)
            is Err -> {
                val err = dRole.value
                if(err is ClientException && err.status.code() == 404) {
                    // role has been deleted, remove configuration
                    dbMentionRole.delete()
                }
                null
            }
        }
    }
}
package moe.kabii.trackers.nitter

import discord4j.common.util.TimestampFormat
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.MessageCreateFields
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.TempStates
import moe.kabii.data.TwitterFeedCache
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.TwitterSettings
import moe.kabii.data.relational.twitter.*
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.MetaData
import moe.kabii.instances.DiscordInstances
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeVideoIntake
import moe.kabii.translation.TranslationResult
import moe.kabii.translation.Translator
import moe.kabii.translation.google.GoogleTranslator
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.*
import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.StringEscapeUtils
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.kotlin.core.publisher.toMono
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.random.Random

class NitterChecker(val instances: DiscordInstances, val cooldowns: ServiceRequestCooldownSpec) : Runnable {
    private val instanceCount = NitterParser.instanceCount
    private val rssThreads = Executors.newFixedThreadPool(instanceCount).asCoroutineDispatcher()
    private val nitterScope = CoroutineScope(rssThreads + CoroutineName("Nitter-RSS-Intake") + SupervisorJob())

    override fun run() {
        if(!MetaData.host) return // do not run on testing instances
        applicationLoop {
            val start = Instant.now()

            LOG.debug("NitterChecker :: start: $start")
            // get all tracked twitter feeds
            // TODO temporary measures
//            val feeds = propagateTransaction {
//                TwitterFeed.all().toList()
//            }
            val feeds = propagateTransaction {
                TwitterFeed.find {
                    TwitterFeeds.enabled eq true
                }.toList()
            }

            if(feeds.isEmpty() || TempStates.skipTwitter) {
                delay(Duration.ofMillis(cooldowns.minimumRepeatTime))
                return@applicationLoop
            }
            // partition feeds onto nitter instances for pulling
            val feedsPerInstance = feeds.size / instanceCount
            val instanceJobs = feeds
                .chunked(feedsPerInstance).withIndex()
                .map { (instanceId, feedChunk) ->

                    LOG.debug("Chunk $instanceId: ${feedChunk.joinToString(", ", transform=TwitterFeed::username)} :: ${NitterParser.getInstanceUrl(instanceId)}")
                    // each chunk executed on different instance - divide up work, pool assigns thread
                    nitterScope.launch {
                        try {
                            var first = true
                            feedChunk.forEach { feed ->

                                propagateTransaction feed@{
                                    if (!first) {
                                        delay(Duration.ofMillis(cooldowns.callDelay))
                                    } else first = false

                                    val targets = getActiveTargets(feed)?.ifEmpty { null }
                                        ?: return@feed // feed untrack entirely or no target channels are currently enabled

                                    val cache = TwitterFeedCache.getOrPut(feed)

                                    val nitter = NitterParser
                                        .getFeed(feed.username, instance = instanceId)
                                        ?: return@feed

                                    val (user, tweets) = nitter

                                    val latest = tweets.maxOfOrNull { tweet ->
                                        // if tweet is after last posted tweet and within 2 hours (arbitrary - to prevent spam when initially tracking) - send discord notifs
                                        val age = Duration.between(tweet.date, Instant.now())

                                        if(tweet.retweet) {
                                            /* Date/time and ID from Nitter feed is of ORIGINAL Tweet, not retweet event
                                            Check if this RT has already been acknowledged from this feed from our own database
                                             */
                                            val new = TwitterRetweets.checkAndUpdate(feed, tweet.id)
                                            if(!new) {
                                                return@maxOfOrNull tweet.id
                                            }
                                            // if temporary switch to syndication feeds - time is accurate again
//                                            if ((feed.lastPulledTweet ?: 0) >= tweet.id
//                                                || age > Duration.ofHours(2)
//                                                || cache.seenTweets.contains(tweet.id)
//                                            ) return@maxOfOrNull tweet.id
                                        } else {
                                            // if already handled or too old, skip, but do not pull tweet ID again
                                            if ((feed.lastPulledTweet ?: 0) >= tweet.id
                                                || age > Duration.ofHours(2)
                                                || cache.seenTweets.contains(tweet.id)
                                            ) return@maxOfOrNull tweet.id
                                        }

                                        notifyTweet(user, tweet, targets)
                                    }
                                    if (latest != null && latest > (feed.lastPulledTweet ?: 0L)) {
                                        transaction {
                                            feed.lastPulledTweet = latest
                                        }
                                    }
                                }
                            }
                        } catch(e: Exception) {
                            LOG.info("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                            LOG.debug(e.stackTraceString)
                        }
                    }
                }
            instanceJobs.joinAll()
            LOG.debug("nitter exit")
            val runDuration = Duration.between(start, Instant.now())
            val delay = cooldowns.minimumRepeatTime - runDuration.toMillis()
            LOG.debug("delay: $delay")
            delay(Duration.ofMillis(max(delay, 0L)))
        }
    }

    @WithinExposedContext
    suspend fun notifyTweet(user: NitterUser, tweet: NitterTweet, targets: List<TwitterTarget>): Long {
        val username = user.username.lowercase()
        LOG.debug("notify ${user.username} tweet - begin -")
        // send discord notifs - check if any channels request
        TwitterFeedCache[username]?.seenTweets?.add(tweet.id)

        // check for youtube video info from tweet
        // often users will make tweets containing video IDs earlier than our other APIs would be aware of them (websub not published immediately for youtube)
        // also will increase awareness of membership-limited streams
        URLUtil.genericUrl
            .findAll(tweet.text)
            .forEach { match ->
                YoutubeVideoIntake.intakeVideosFromText(match.value)
            }

        // cache to not repeat request for twitter video
        val twitterVid by lazy {
            // process video attachment
            tweet.videoUrl
                ?: try {
                    NitterParser.getVideoFromTweet(tweet.id)
                } catch(e: Exception) {
                    LOG.warn("Error getting V1 Tweet from feed: ${tweet.id}")
                    null
                }
        }

        // cache to not repeat translation for same tweet across multiple channels/servers
        val translations = mutableMapOf<String, TranslationResult>()

        targets.forEach target@{ target ->
            val fbk = instances[target.discordClient]
            val discord = fbk.client
            try {
                // post a notif to this target
                val channel = discord.getChannelById(target.discordChannel.channelID.snowflake)
                    .ofType(MessageChannel::class.java)
                    .awaitSingle()

                val features = GuildConfigurations.findFeatures(target)
                val twitter = features?.twitterSettings ?: TwitterSettings()

                if(!tweet.notifyOption.get(twitter)) return@target

                val action = when {
                    tweet.retweet -> "retweeted **@${tweet.retweetOf}** \uD83D\uDD01"
                    tweet.reply -> "replied to a Tweet from **@${tweet.replyTo}** \uD83D\uDCAC"
                    tweet.quote -> "quoted a Tweet from **@${tweet.quoteOf}** \uD83D\uDDE8"
                    else -> "posted a new Tweet"
                }

                // LOG.debug("translation stage")
                val translation = if(twitter.autoTranslate && tweet.text.isNotBlank()) {
                    try {
                        val lang = GuildConfigurations
                            .getOrCreateGuild(fbk.clientId, target.discordChannel.guild!!.guildID)
                            .translator.defaultTargetLanguage

                        val translator = Translator.getService(tweet.text, listOf(lang), twitterFeed = username, primaryTweet = !tweet.retweet)

                        // check cache for existing translation of this tweet
                        val standardLangTag = Translator.baseService.supportedLanguages[lang]?.tag ?: lang
                        val existingTl = translations[standardLangTag]
                        val translation = if(existingTl != null && (existingTl.service == GoogleTranslator || translator.service != GoogleTranslator)) existingTl else {

                            val tl = translator.translate(from = null, to = translator.getLanguage(lang), text = tweet.text)
                            translations[standardLangTag] = tl
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
                val attachment = tweet.images.firstOrNull()
                val size = tweet.images.size
                var attachInfo = ""

                when {
                    tweet.hasVideo -> {
                        attachInfo = "(Open on Twitter to view video)\n"
                        // get potentially cached twitter video url
                        attachedVideo = twitterVid

                        if(attachedVideo == null) {
                            // if we can't provide video, revert to notifying user/regular thumbnail attachment
                            if(attachment != null) {
                                editedThumb = TwitterThumbnailGenerator.attachInfoTag(attachment, video = true)
                            }
                        }
                    }
                    size > 1 -> {
                        attachInfo = "(Open on Twitter to view $size images)\n"
                        // tag w/ number of photos
                        if(attachment != null) {
                            editedThumb = TwitterThumbnailGenerator.attachInfoTag(attachment, imageCount = size)
                        }
                    }
                }

                LOG.debug("roles phase")
                // mention roles
                val mention = getMentionRoleFor(target, channel, tweet, twitter)

                var outdated = false
                val mentionText = if(mention != null) {
                    outdated = !tweet.retweet && Duration.between(tweet.date, Instant.now()) > Duration.ofMinutes(15)
                    val rolePart = if(mention.discord == null || outdated) null
                    else mention.discord.mention.plus(" ")
                    val textPart = mention.db.mentionText?.plus(" ")
                    "${rolePart ?: ""}${textPart ?: ""}"
                } else ""

                val notifSpec = MessageCreateSpec.create()
                    .run {
                        val timestamp = if(!tweet.retweet) TimestampFormat.RELATIVE_TIME.format(tweet.date) else ""
                        withContent("$mentionText**@${user.username}** $action $timestamp: https://twitter.com/${user.username}/status/${tweet.id}")
                    }
                    .run {
                        if(editedThumb != null) withFiles(MessageCreateFields.File.of("thumbnail_edit.png", editedThumb)) else this
                    }
                    .run {
                        val footer = StringBuilder(attachInfo)

                        val color = mention?.db?.embedColor ?: 1942002 // hardcoded 'twitter blue' if user has not customized the color
                        val author = if(tweet.retweet) tweet.retweetOf!! else user.username
                        val avatar = if(tweet.retweet) NettyFileServer.twitterLogo else user.avatar // no way to easily get the retweeted user's pfp on nitter implementation

                        val embed = Embeds.other(StringEscapeUtils.unescapeHtml4(tweet.text), Color.of(color))
                            .withAuthor(EmbedCreateFields.Author.of("@$author", URLUtil.Twitter.feedUsername(author), avatar))
                            .run {
                                val fields = mutableListOf<EmbedCreateFields.Field>()

                                if(tweet.quote) {
                                    fields.add(EmbedCreateFields.Field.of("**Tweet Quoted**", tweet.quoteTweetUrl, false))
                                }

                                if(translation != null) {
                                    val tlText = StringUtils.abbreviate(StringEscapeUtils.unescapeHtml4(translation.translatedText), MagicNumbers.Embed.FIELD.VALUE)
                                    footer.append("Translator: ${translation.service.fullName}, ${translation.originalLanguage.tag} -> ${translation.targetLanguage.tag}\n")
                                    fields.add(EmbedCreateFields.Field.of("**Tweet Translation**", tlText, false))
                                }

                                if(fields.isNotEmpty()) withFields(fields) else this
                            }
                            .run {
                                // TODO temporary measures
                                if(Random.nextInt(25) == 0) {
                                    footer.append("Twitter tracking is only enabled for specific feeds due to the current Twitter issues and may stop working if Twitter makes more changes. Please be patient!\n")
                                }
                                if(outdated) footer.append("Skipping ping for old Tweet.\n")
                                if(outdated) LOG.info("Missed ping: $tweet")
                                if(footer.isNotBlank()) withFooter(EmbedCreateFields.Footer.of(footer.toString(), NettyFileServer.twitterLogo)) else this
                            }
                            .run {
                                if(editedThumb != null) {
                                    // always use our edited thumbnail if we produced one for this
                                    withImage("attachment://thumbnail_edit.png")
                                } else if(attachment != null) withImage(attachment)
                                else this
                            }
                        withEmbeds(embed)
                    }

                if(twitter.mediaOnly && attachment == null) return@target
                val notif = channel.createMessage(notifSpec).awaitSingle()

                if(attachedVideo != null) {
                    channel.createMessage(attachedVideo)
                        .withMessageReference(notif.id)
                        .tryAwait()
                }

                TrackerUtil.checkAndPublish(fbk, notif)
            } catch (e: Exception) {
                if (e is ClientException && e.status.code() == 403) {
                    TrackerUtil.permissionDenied(fbk, target.discordChannel.guild?.guildID, target.discordChannel.channelID, FeatureChannel::twitterTargetChannel, target::delete)
                    LOG.warn("Unable to send Tweet to channel '${target.discordChannel.channelID}'. Disabling feature in channel. TwitterChecker.java")
                } else {
                    LOG.warn("Error sending Tweet to channel: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }
        }
        LOG.debug("notify ${user.username} - complete?? -")
        return tweet.id // return tweet id for 'max' calculation to find the newest tweet that was returned
    }

    @WithinExposedContext
    suspend fun getActiveTargets(feed: TwitterFeed): List<TwitterTarget>? {
        val existingTargets = feed.targets.toList()
            .filter { target ->
                val discord = instances[target.discordClient].client
                // untrack target if discord channel is deleted
                if (target.discordChannel.guild != null) {
                    try {
                        discord.getChannelById(target.discordChannel.channelID.snowflake).awaitSingle()
                    } catch (e: Exception) {
                        if(e is ClientException) {
                            if(e.status.code() == 401) return emptyList()
                            if(e.status.code() == 404) {
                                LOG.info("Untracking Twitter feed '${feed.username}' in ${target.discordChannel.channelID} as the channel seems to be deleted.")
                                target.delete()
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
                GuildConfigurations.findFeatures(target)?.twitterTargetChannel != false // enabled or DM
            }
        } else {
            feed.delete()
            LOG.info("Untracking Twitter feed ${feed.username} as it has no targets.")
            null
        }
    }

    data class TwitterMentionRole(val db: TwitterTargetMention, val discord: Role?)
    @WithinExposedContext
    suspend fun getMentionRoleFor(dbTarget: TwitterTarget, targetChannel: MessageChannel, tweet: NitterTweet, twitterCfg: TwitterSettings): TwitterMentionRole? {
        // do not return ping if not configured for channel/tweet type
        when {
            !twitterCfg.mentionRoles -> return null
            tweet.retweet -> if(!twitterCfg.mentionRetweets) return null
            tweet.reply -> if(!twitterCfg.mentionReplies) return null
            tweet.quote -> if(!twitterCfg.mentionQuotes) return null
            else -> if(!twitterCfg.mentionTweets) return null
        }

        val dbMentionRole = dbTarget.mention() ?: return null
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
                    if(dbMentionRole.mentionText != null) {
                        // don't delete if mentionrole still has text component
                        dbMentionRole.mentionRole = null
                    } else {
                        dbMentionRole.delete()
                    }
                }
                null
            }
            null -> null
        }
        return TwitterMentionRole(dbMentionRole, discordRole)
    }
}
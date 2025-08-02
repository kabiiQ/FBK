package moe.kabii.trackers.posts.twitter

import discord4j.common.util.TimestampFormat
import discord4j.core.`object`.component.*
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.MessageCreateFields
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.flat.Keys
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.PostsSettings
import moe.kabii.data.relational.posts.twitter.NitterFeed
import moe.kabii.data.relational.posts.twitter.NitterFeeds
import moe.kabii.data.relational.posts.twitter.NitterRetweets
import moe.kabii.data.temporary.Cache
import moe.kabii.data.temporary.TwitterFeedCache
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.net.NettyFileServer
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.posts.PostWatcher
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeVideoIntake
import moe.kabii.translation.TranslationResult
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.constants.Opcode
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.*
import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.StringEscapeUtils
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max

open class NitterChecker(instances: DiscordInstances) : Runnable, PostWatcher(instances) {
    private val instanceCount = NitterParser.instanceCount
    private val generalInstanceAdjustment = 0L
    private val minimumRepeatTime = refreshGoal + 2_000L

    private val metaChanId = Keys.config[Keys.Admin.logChannel].snowflake
    private val errorPostCooldown = Duration.ofHours(8) // will only post rate limit to discord every duration
    private var errorPostNext = Instant.now()

    private val instanceLocks: Map<Int, Mutex>

    companion object {
        val callDelay = 3_500L // 3.5sec/call = 17/min = 257/15min
        //val callDelay = 3_600L // 3.6sec/call = 16.6/min = 250/15min
        //val callDelay = 6_000L // 6sec/call = 10/min = 150/15min
        val refreshGoal = 180_000L // = 53/instance @ 3.6 seconds
        val loopTime = 20_000L // can lower loop repeat time below refresh time now with locking system implemented
    }

    init {
        instanceLocks = (0 until instanceCount).associateWith {
            Mutex()
        }
    }

    override fun run() {
        applicationLoop {
            val start = Instant.now()
            try {
                updateFeeds(start)
            } catch(e: Exception) {
                LOG.warn("NitterChecker: ${e.message}")
                LOG.debug(e.stackTraceString)
            }

            val runDuration = Duration.between(start, Instant.now())
            val delay = loopTime - runDuration.toMillis()
            LOG.debug("delay: $delay")
            delay(Duration.ofMillis(max(delay, 0L)))
        }
    }

    open suspend fun updateFeeds(start: Instant) {
        LOG.debug("NitterChecker :: start: $start")

        // get all tracked twitter feeds
        val feeds = propagateTransaction {
            NitterFeed
                .find {
                    NitterFeeds.enabled eq true
                }
                .associateWith(NitterFeed::feed)
                .toList()
        }

        if(feeds.isEmpty() || Cache.skipTwitter) {
            delay(Duration.ofMillis(minimumRepeatTime))
            return
        }

        val feedsPerChunk = ceil(feeds.size.toDouble() / instanceCount).toInt()
        val chunks = feeds
            .chunked(feedsPerChunk)
            .mapIndexed { chunkIndex, chunkFeeds ->
                chunkIndex to chunkFeeds
            }
        chunks
            .map { (instanceId, feedChunk) ->

                LOG.debug("Chunk $instanceId (${feedChunk.size}): ${feedChunk.joinToString(", ") { (feed, _) -> feed.username } } :: ${NitterParser.getInstanceUrl(instanceId)}")
                // each chunk executed on different instance - divide up work, pool assigns thread
                taskScope.launch {
                    val lock = instanceLocks.getValue(instanceId)
                    if(!lock.tryLock()) {
                        LOG.debug("Skipping in-use nitter instance #$instanceId")
                        return@launch
                    }
                    try {
                        val timeout = max(feedChunk.size * callDelay * 1.5, 720000.0)
                        kotlinx.coroutines.time.withTimeout(Duration.ofMillis(timeout.toLong())) {
                            var first = true
                            feedChunk.forEach { (feed, parent) ->

                                if (!first) {
                                    // val delay = if(instanceId >= priorityInstance) callDelay + generalInstanceAdjustment else callDelay
                                    val delay = callDelay
                                    delay(delay)
                                } else first = false

                                val targets = getActiveTargets(parent)?.ifEmpty { null }
                                    ?: return@forEach // feed untrack entirely or no target channels are currently enabled

                                val cache = TwitterFeedCache.getOrPut(feed)

                                // Create a callback that will be used in the event of nitter rate limit - only call out to discord after the errorPostCooldown interval
                                suspend fun rateLimit() {
                                    if(Instant.now() > errorPostNext) {
                                        errorPostNext = Instant.now() + errorPostCooldown // update throttled next post
                                        try {
                                            // post actual error notification to admin channel
                                            instances.all().first().client
                                                .getChannelById(metaChanId)
                                                .ofType(MessageChannel::class.java)
                                                .flatMap { chan ->
                                                    chan.createMessage(
                                                        Embeds.fbk("A Twitter rate limit error has occured for instance #$instanceId")
                                                    )
                                                }
                                                .awaitSingle()
                                        } catch(e: Exception) {
                                            LOG.warn("Error posting Twitter rate limit meta message: ${e.message}")
                                            LOG.debug(e.stackTraceString)
                                        }
                                    }
                                }

                                val nitter = NitterParser
                                    .getFeed(feed.username, instance = instanceId, rateLimitCallback = ::rateLimit)
                                    ?: return@forEach

                                val (user, tweets) = nitter

                                val latest = tweets.maxOfOrNull { tweet ->
                                    // if tweet is after last posted tweet and within 2 hours (arbitrary - to prevent spam when initially tracking) - send discord notifs
                                    val age = Duration.between(tweet.date, Instant.now())

                                    propagateTransaction {
                                        if (tweet.retweet) {
                                            /* Date/time and ID from Nitter feed is of ORIGINAL Tweet, not retweet event
                                            Check if this RT has already been acknowledged from this feed from our own database
                                             */
                                            val new = NitterRetweets.checkAndUpdate(feed, tweet.id)
                                            if (!new) {
                                                return@propagateTransaction tweet.id
                                            }
                                        } else {
                                            // if already handled or too old, skip, but do not pull tweet ID again
                                            if ((feed.lastPulledTweet ?: 0) >= tweet.id
                                                || age > Duration.ofHours(1)
//                                                || age > Duration.ofHours(12)
                                                || cache.seenTweets.contains(tweet.id)
                                            ) return@propagateTransaction tweet.id
                                        }

                                        notifyTweet(user, tweet, targets)
                                        tweet.id
                                    }
                                }
                                if (latest != null && latest > (feed.lastPulledTweet ?: 0L)) {
                                    propagateTransaction {
                                        feed.lastPulledTweet = latest
                                    }
                                }
                            }
                        }
                        lock.unlock()
                    } catch(time: TimeoutCancellationException) {
                        LOG.warn("NitterChecker routine: timeout reached :: $instanceId")
                    } catch(e: Exception) {
                        LOG.info("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                        LOG.debug(e.stackTraceString)
                    } finally {
                        if(lock.isLocked) lock.unlock()
                    }
                }
            }
        LOG.debug("nitter exit")
    }

    fun notifyTweet(user: NitterUser, tweet: NitterTweet, targets: List<TrackedSocialTarget>) {
        val username = user.username.lowercase()
        LOG.debug("notify ${user.username} tweet - begin -")
        // send discord notifs - check if any channels request
        TwitterFeedCache[username]?.seenTweets?.add(tweet.id)

        discordTask(30_000L) {
            // check for youtube video info from tweet
            // often users will make tweets containing video IDs earlier than our other APIs would be aware of them (websub not published immediately for youtube)
            // also will increase awareness of membership-limited streams
            URLUtil.genericUrl
                .findAll(tweet.text)
                .forEach { match ->
                    YoutubeVideoIntake.intakeVideosFromText(match.value)
                }

            // cache to not repeat translation for same tweet across multiple channels/servers
            val translations = mutableMapOf<String, TranslationResult>()

            targets.forEach target@{ target ->
                val fbk = instances[target.discordClient]
                val discord = fbk.client
                try {
                    // post a notif to this target
                    val channel = discord.getChannelById(target.discordChannel)
                        .ofType(MessageChannel::class.java)
                        .awaitSingle()

                    val (_, features) = GuildConfigurations.findFeatures(target.discordClient, target.discordGuild?.asLong(), target.discordChannel.asLong())
                    val postCfg = features?.postsSettings ?: PostsSettings()

                    if(!tweet.notifyOption.get(postCfg)) return@target

                    val tlSettings = GuildConfigurations
                        .getOrCreateGuild(fbk.clientId, target.discordGuild!!.asLong())
                        .translator

                    val translation = translatePost(tweet.text, tweet.retweet, username, targets, tlSettings, postCfg, translations)

                    // get role to be mentioned
                    val mention = getMentionRoleFor(target, channel, postCfg, tweet.mentionOption)
                    val outdated = !tweet.retweet && Duration.between(tweet.date, Instant.now()) > Duration.ofMinutes(15)
                    val mentionText = mention?.toText(!outdated, user.name, tweet.date, user.username, tweet.url) ?: ""

                    val timestamp = if(!tweet.retweet) TimestampFormat.RELATIVE_TIME.format(tweet.date) else ""

                    val tweetUser = tweet.retweetOf ?: user.username
                    val tweetPath = "$tweetUser/status/${tweet.id}"
                    val url = if(postCfg.customTwitterDomain?.contains("&tweet") == true) {
                        // custom twitter path with variables
                        // fixvx.com/&path/en -> fixupx.com/user/status/id/en
                        postCfg.customTwitterDomain!!.replace("&tweet", tweetPath)
                    } else {
                        // fixvx.com -> fixvx.com/user/status/id
                        val domain = postCfg.customTwitterDomain ?: "x.com"
                        "$domain/$tweetPath"
                    }
                    val event = when {
                        tweet.retweet -> "reposted **@${tweet.retweetOf}** \uD83D\uDD01"
                        tweet.reply -> "replied to **@${tweet.replyTo}** \uD83D\uDCAC"
                        tweet.quote -> "quoted a post from **@${tweet.quoteOf}** \uD83D\uDDE8"
                        else -> "made a new post"
                    }
                    val action = "**@${user.username}** $event $timestamp: https://$url"

                    val color = mention?.db?.embedColor ?: 1942002 // hardcoded 'twitter blue' if user has not customized the color
                    val author = if(tweet.retweet) tweet.retweetOf!! else user.username
                    val text = tweet.text.escapeMarkdown()

                    val attachment = tweet.images.firstOrNull()
                    val size = tweet.images.size
                    var attachedVideo: String? = null
                    var attachInfo = ""

                    // Determine embed generation based on user settings in channel
                    val notifSpec = when {
                        postCfg.mediaOnly && attachment == null -> return@target // do not send tweet at all
                        postCfg.customTwitterDomain != null -> MessageCreateSpec.create().withContent("$mentionText$action") // send url, allow discord to generate the embed
                        postCfg.useComponents -> {
                            // Use newer Discord componentsv2 to generate message with potential image gallery
                            val media = tweet.images + tweet.videos
                            val container = Container.of(
                                Color.of(color),
                                listOfNotNull(
                                    if(tweet.retweet) TextDisplay.of(action)
                                    else Section.of(
                                        Thumbnail.of(
                                            UnfurledMediaItem.of(user.avatar)
                                        ),
                                        TextDisplay.of(action)
                                    ),

                                    TextDisplay.of(text),

                                    if(media.any()) MediaGallery.of(
                                        media.map(UnfurledMediaItem::of).map(MediaGalleryItem::of)
                                    ) else null,

                                    if(translation != null) {
                                        val tlText = StringEscapeUtils.unescapeHtml4(translation.translatedText)
                                        TextDisplay.of("**Post Translation** (${translation.service.fullName}, _${translation.originalLanguage.tag} -> ${translation.targetLanguage.tag})_\n$tlText")
                                    } else null,

                                    if(tweet.quote) Section.of(
                                        Button.link(tweet.quoteTweetUrl, "View on X"),
                                        TextDisplay.of("Tweet Quoted:")
                                    ) else null,

                                    if(mentionText.isNotBlank()) TextDisplay.of(mentionText)
                                    else null,

                                    if(outdated && mention != null) {
                                        LOG.info("Missed ping: $tweet")
                                        TextDisplay.of("-# Ping was skipped for this late Tweet.")
                                    } else null
                                )
                            )

                            MessageCreateSpec.create()
                                .withFlags(Message.Flag.IS_COMPONENTS_V2)
                                .withComponents(container)
                        }
                        else -> {
                            // Generate Discord embed similar to normal Twitter links
                            var editedThumb: ByteArrayInputStream? = null
                            when {
                                tweet.videos.any() -> {
                                    attachInfo = "(Open on Twitter to view video)\n"
                                    // get potentially cached twitter video url
                                    attachedVideo = tweet.videos.first()
                                }
                                size > 1 -> {
                                    attachInfo = "(Open on Twitter to view $size images)\n"
                                    // tag w/ number of photos
                                    if(attachment != null) {
                                        editedThumb = TwitterThumbnailGenerator.attachInfoTag(attachment, imageCount = size)
                                    }
                                }
                            }

                            MessageCreateSpec.create()
                                .withContent("$mentionText$action")
                                .run {
                                    if(editedThumb != null) withFiles(MessageCreateFields.File.of("thumbnail_edit.png", editedThumb)) else this
                                }
                                .run {
                                    val footer = StringBuilder(attachInfo)
                                    val avatar = if(tweet.retweet) NettyFileServer.twitterLogo else user.avatar

                                    val embed = Embeds.other(text, Color.of(color))
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
                                            if(outdated && mention != null) {
                                                footer.append("Skipping ping for old Tweet.\n")
                                                LOG.info("Missed ping: $tweet")
                                            }
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
                        }
                    }

                    val notif = channel
                        .createMessage(notifSpec)
                        .timeout(Duration.ofMillis(24_000L))
                        .awaitSingle()

                    // Send message 'replies' if features are enabled but not supported by notification style
                    if(!tweet.retweet && attachedVideo != null) {
                        channel.createMessage(attachedVideo)
                            .withMessageReference(notif.id.reference)
                            .tryAwait()
                    }

                    if(postCfg.customTwitterDomain != null && translation != null) {
                        val translationReply = Embeds.fbk(StringUtils.abbreviate(StringEscapeUtils.unescapeHtml4(translation.translatedText), MagicNumbers.Embed.MAX_DESC))
                            .withTitle("@${user.username} Tweet Translation")
                            .withFooter(EmbedCreateFields.Footer.of("Translator: ${translation.service.fullName}, ${translation.originalLanguage.tag} -> ${translation.targetLanguage.tag}\n", null))

                        channel.createMessage(translationReply)
                            .withMessageReference(notif.id.reference)
                            .tryAwait()
                    }

                    TrackerUtil.checkAndPublish(fbk, notif)
                } catch (time: TimeoutCancellationException) {
                    LOG.warn("Timeout sending ${target.username} Tweet to ${target.discordClient}/${target.discordGuild?.asString()}/${target.discordChannel.asString()}")
                } catch (e: Exception) {
                    if (e is ClientException && Opcode.denied(e.opcode)) {
                        TrackerUtil.permissionDenied(fbk, target.discordGuild, target.discordChannel, FeatureChannel::postsTargetChannel) { target.findDbTarget().delete() }
                        LOG.warn("Unable to send Tweet to channel '${target.discordClient}/${target.discordGuild?.asString()}/${target.discordChannel.asString()}'. Disabling feature in channel. TwitterChecker.java")
                    } else {
                        LOG.warn("Error sending Tweet to channel ${target.discordClient}/${target.discordGuild?.asString()}/${target.discordChannel.asString()}: ${e.message}")
                        LOG.debug(e.stackTraceString)
                    }
                }
            }
            LOG.debug("notify ${user.username} - complete?? -")
        }
    }
}
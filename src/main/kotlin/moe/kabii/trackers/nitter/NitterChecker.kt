package moe.kabii.trackers.nitter

import discord4j.common.util.Snowflake
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.TempStates
import moe.kabii.data.TwitterFeedCache
import moe.kabii.data.flat.Keys
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.TwitterSettings
import moe.kabii.data.relational.twitter.*
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.MetaData
import moe.kabii.instances.DiscordInstances
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
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
import reactor.kotlin.core.publisher.toMono
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max

open class NitterChecker(val instances: DiscordInstances) : Runnable {
    private val instanceCount = NitterParser.instanceCount
    private val nitterScope = CoroutineScope(DiscordTaskPool.streamThreads + CoroutineName("Nitter-RSS-Intake") + SupervisorJob())

    private val generalInstanceAdjustment = 0L
    private val minimumRepeatTime = refreshGoal + 2_000L

    private val metaChanId = Keys.config[Keys.Admin.logChannel].snowflake
    private val errorPostCooldown = Duration.ofHours(4) // will only post rate limit to discord every duration
    private var errorPostNext = Instant.now()

    private val instanceLocks: Map<Int, Mutex>

    companion object {
        //val callDelay = 3_500L // 3.5sec/call = 17/min = 257/15min
        val callDelay = 3_600L // 3.6sec/call = 16.6/min = 250/15min
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
        if(!MetaData.host) return // do not run on testing instances
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

    suspend fun <T> discordTask(timeoutMillis: Long = 6_000L, block: suspend() -> T) = nitterScope.launch {
        withTimeout(timeoutMillis) {
            block()
        }
    }

    open suspend fun updateFeeds(start: Instant) {
        LOG.debug("NitterChecker :: start: $start")

        // get all tracked twitter feeds
        val feeds = propagateTransaction {
            TwitterFeed
                .find {
                    TwitterFeeds.enabled eq true
                }
                .toList()
        }

        if(feeds.isEmpty() || TempStates.skipTwitter) {
            delay(Duration.ofMillis(minimumRepeatTime))
            return
        }

        //val (priority, general) = feeds.partition(TwitterFeed::enabled)

        // compute how many instances to use for 'priority' feeds, targeting a refresh time goal
        // ensure at least one instance is saved for general pool in case numbers change drastically
//        val priorityInstance = min(ceil((priority.size.toDouble() * callDelay) / refreshGoal).toInt(), instanceCount)
//        val generalInstance = instanceCount - priorityInstance

        // convert list of feeds into Map<Instance ID, Feed Chunk>
        // partition feeds onto nitter instances for pulling
//        val feedPerPriority = ceil(priority.size.toDouble() / priorityInstance).toInt()
//        val priorityChunks = priority
//            .chunked(feedPerPriority)
//            .mapIndexed { chunkIndex, chunkFeeds ->
//                chunkIndex to chunkFeeds
//            }

//        val feedPerGeneral = ceil(general.size.toDouble() / generalInstance).toInt()
//        val generalChunks = general
//            .chunked(feedPerGeneral)
//            .mapIndexed { chunkIndex, chunkFeeds ->
//                chunkIndex + priorityInstance to chunkFeeds
//            }

        val feedsPerChunk = ceil(feeds.size.toDouble() / instanceCount).toInt()
        val chunks = feeds
            .chunked(feedsPerChunk)
            .mapIndexed { chunkIndex, chunkFeeds ->
                chunkIndex to chunkFeeds
            }
        chunks
            .map { (instanceId, feedChunk) ->

                LOG.debug("Chunk $instanceId (${feedChunk.size}): ${feedChunk.joinToString(", ", transform=TwitterFeed::username)} :: ${NitterParser.getInstanceUrl(instanceId)}")
                // each chunk executed on different instance - divide up work, pool assigns thread
                nitterScope.launch {
                    val lock = instanceLocks.getValue(instanceId)
                    if(!lock.tryLock()) {
                        LOG.debug("Skipping in-use nitter instance #$instanceId")
                        return@launch
                    }
                    try {
                        val timeout = max(feedChunk.size * callDelay * 1.5, 720000.0)
                        kotlinx.coroutines.time.withTimeout(Duration.ofMillis(timeout.toLong())) {
                            var first = true
                            feedChunk.forEach { feed ->

                                if (!first) {
                                    // val delay = if(instanceId >= priorityInstance) callDelay + generalInstanceAdjustment else callDelay
                                    val delay = callDelay
                                    delay(delay)
                                } else first = false

                                val targets = getActiveTargets(feed)?.ifEmpty { null }
                                    ?: return@forEach // feed untrack entirely or no target channels are currently enabled

                                val cache = TwitterFeedCache.getOrPut(feed)

                                // Create a callback that will be used in the event of nitter rate limit - only call out to discord after the errorPostCooldown interval
                                suspend fun rateLimit() {
                                    if(Instant.now() > errorPostNext) {
                                        errorPostNext = Instant.now() + errorPostCooldown // update throttled next post
                                        try {
                                            // post actual error notification to admin channel
                                            instances[instanceId].client
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
                                            val new = TwitterRetweets.checkAndUpdate(feed, tweet.id)
                                            if (!new) {
                                                return@propagateTransaction tweet.id
                                            }
                                            // if temporary switch to syndication feeds - time is accurate again
    //                                            if ((feed.lastPulledTweet ?: 0) >= tweet.id
    //                                                || age > Duration.ofHours(2)
    //                                                || cache.seenTweets.contains(tweet.id)
    //                                            ) return@maxOfOrNull tweet.id
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

    suspend fun notifyTweet(user: NitterUser, tweet: NitterTweet, targets: List<TrackedTwitTarget>) {
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
                    val channel = discord.getChannelById(target.discordChannel)
                        .ofType(MessageChannel::class.java)
                        .awaitSingle()

                    val (_, features) = GuildConfigurations.findFeatures(target.discordClient, target.discordGuild?.asLong(), target.discordChannel.asLong())
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
                            val tlConfig = GuildConfigurations
                                .getOrCreateGuild(fbk.clientId, target.discordGuild!!.asLong())
                                .translator

                            // Retweets default to low-quality local translations. If "skipRetweets" is set by user, retweets should just forego translation.
                            if(!tweet.retweet || !tlConfig.skipRetweets) {

                                val lang = tlConfig.defaultTargetLanguage
                                val translator = Translator.getService(tweet.text, listOf(lang), twitterFeed = username, primaryTweet = !tweet.retweet, guilds = targets.mapNotNull(TrackedTwitTarget::discordGuild))

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
                            } else null

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
                    if(twitter.mediaOnly && attachment == null) return@target

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

                    val baseNotif = MessageCreateSpec.create()
                        .run {
                            val domain = if(twitter.customDomain != null) twitter.customDomain else "twitter.com"
                            val timestamp = if(!tweet.retweet) TimestampFormat.RELATIVE_TIME.format(tweet.date) else ""
                            withContent("$mentionText**@${user.username}** $action $timestamp: https://$domain/${user.username}/status/${tweet.id}")
                        }

                    // If the user has set a custom domain to be used, we just post the URL and let Discord handle the generation
                    val notifSpec = if(twitter.customDomain != null) baseNotif else {
                        baseNotif
                            .run {
                                if(editedThumb != null) withFiles(MessageCreateFields.File.of("thumbnail_edit.png", editedThumb)) else this
                            }
                            .run {
                                val footer = StringBuilder(attachInfo)

                                val color = mention?.db?.embedColor ?: 1942002 // hardcoded 'twitter blue' if user has not customized the color
                                val author = if(tweet.retweet) tweet.retweetOf!! else user.username
                                val avatar = if(tweet.retweet) NettyFileServer.twitterLogo else user.avatar // no way to easily get the retweeted user's pfp on nitter implementation

                                val text = StringEscapeUtils
                                    .unescapeHtml4(tweet.text)
                                    .replace("*", "\\*")
                                    .replace("_ ", "\\_ ")
                                    .replace(" _", " \\_")
                                    .replace("#", "\\#")
                                    .replace("~", "\\~")
                                    .replace("|", "\\|")
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
                    }

                    val notif = channel
                        .createMessage(notifSpec)
                        .timeout(Duration.ofMillis(6_000L))
                        .awaitSingle()

                    if(attachedVideo != null) {
                        channel.createMessage(attachedVideo)
                            .withMessageReference(notif.id)
                            .tryAwait()
                    }

                    if(twitter.customDomain != null && translation != null) {
                        val translationReply = Embeds.fbk(StringUtils.abbreviate(StringEscapeUtils.unescapeHtml4(translation.translatedText), MagicNumbers.Embed.MAX_DESC))
                            .withTitle("@${user.username} Tweet Translation")
                            .withFooter(EmbedCreateFields.Footer.of("Translator: ${translation.service.fullName}, ${translation.originalLanguage.tag} -> ${translation.targetLanguage.tag}\n", null))

                        channel.createMessage(translationReply)
                            .withMessageReference(notif.id)
                            .tryAwait()
                    }

                    TrackerUtil.checkAndPublish(fbk, notif)
                } catch (time: TimeoutCancellationException) {
                    LOG.warn("Timeout sending ${target.username} Tweet to ${target.discordClient}/${target.discordGuild?.asString()}/${target.discordChannel.asString()}")
                } catch (e: Exception) {
                    if (e is ClientException && e.status.code() == 403) {
                        TrackerUtil.permissionDenied(fbk, target.discordGuild, target.discordChannel, FeatureChannel::twitterTargetChannel) { propagateTransaction { target.findDBTarget().delete() } }
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

    @CreatesExposedContext
    suspend fun getActiveTargets(feed: TwitterFeed): List<TrackedTwitTarget>? {
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
                                LOG.info("Untracking Twitter feed '${feed.username}' in ${target.discordChannel} as the channel seems to be deleted.")
                                propagateTransaction {
                                    target.findDBTarget().delete()
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
                featureChannel.twitterTargetChannel
            }
        } else {
            LOG.info("Twitter feed ${feed.username} returned NO active targets.")
            return null
            propagateTransaction {
                feed.delete()
            }
            LOG.info("Untracking Twitter feed ${feed.username} as it has no targets.")
            null
        }
    }

    /**
     * Object to hold information about a tracked target from the database - resolving references to reduce transactions later
     */
    data class TrackedTwitTarget(
        val db: Int,
        val discordClient: Int,
        val dbFeed: Int,
        val username: String,
        val discordChannel: Snowflake,
        val discordGuild: Snowflake?,
        val userId: Snowflake
    ) {
        @RequiresExposedContext fun findDBTarget() = TwitterTarget.findById(db)!!
    }

    @RequiresExposedContext
    suspend fun loadTarget(target: TwitterTarget) =
        TrackedTwitTarget(
            target.id.value,
            target.discordClient,
            target.twitterFeed.id.value,
            target.twitterFeed.username,
            target.discordChannel.channelID.snowflake,
            target.discordChannel.guild?.guildID?.snowflake,
            target.tracker.userID.snowflake
        )

    data class TwitterMentionRole(val db: TwitterTargetMention, val discord: Role?)
    @CreatesExposedContext
    suspend fun getMentionRoleFor(dbTarget: TrackedTwitTarget, targetChannel: MessageChannel, tweet: NitterTweet, twitterCfg: TwitterSettings): TwitterMentionRole? {
        // do not return ping if not configured for channel/tweet type
        when {
            !twitterCfg.mentionRoles -> return null
            tweet.retweet -> if(!twitterCfg.mentionRetweets) return null
//            tweet.reply -> if(!twitterCfg.mentionReplies) return null
            tweet.quote -> if(!twitterCfg.mentionQuotes) return null
            else -> if(!twitterCfg.mentionTweets) return null
        }

        val dbMentionRole = propagateTransaction {
            dbTarget.findDBTarget().mention()
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
        return TwitterMentionRole(dbMentionRole, discordRole)
    }
}
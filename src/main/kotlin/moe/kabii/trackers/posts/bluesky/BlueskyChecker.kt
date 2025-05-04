package moe.kabii.trackers.posts.bluesky

import discord4j.common.util.TimestampFormat
import discord4j.core.`object`.component.*
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.PostsSettings
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.data.relational.posts.bluesky.BlueskyFeed
import moe.kabii.data.temporary.Locks
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.TrackerErr
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.posts.PostWatcher
import moe.kabii.trackers.posts.bluesky.xrpc.BlueskyParser
import moe.kabii.trackers.posts.bluesky.xrpc.BlueskyRecheck
import moe.kabii.trackers.posts.bluesky.xrpc.json.*
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeVideoIntake
import moe.kabii.translation.TranslationResult
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.*
import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.StringEscapeUtils
import org.joda.time.DateTime
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class BlueskyChecker(val cooldowns: ServiceRequestCooldownSpec, instances: DiscordInstances): Runnable, PostWatcher(instances) {

    override fun run() {
        applicationLoop {
            val start = Instant.now()
            try {
                updateFeeds()
            } catch(e: Exception) {
                LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            val runDuration = Duration.between(start, Instant.now())
            val delay = cooldowns.minimumRepeatTime - runDuration.toMillis()
            delay(max(delay, 0L))
        }
    }

    data class FeedInfo(
        val dbFeed: BlueskyFeed,
        val dbParent: TrackedSocialFeeds.SocialFeed,
        val did: String,
        val handle: String,
        val displayName: String,
        val lastUpdated: DateTime?
    )

    @RequiresExposedContext
    fun loadFeed(feed: BlueskyFeed) = FeedInfo(
        feed,
        feed.feed,
        feed.did,
        feed.handle,
        feed.displayName.ifBlank { "@${feed.handle}" },
        feed.lastPulledTime
    )

    private suspend fun updateFeeds() {
        // Get all tracked Bluesky feeds
        val feeds = propagateTransaction {
            BlueskyFeed
                .all()
                .map(::loadFeed)
                .toList()
        }

        feeds.forEach { feed ->
            updateFeed(feed)
        }
    }

    suspend fun getFeed(identifier: String) = propagateTransaction {
        BlueskyFeed
            .findExisting(identifier)
            ?.run(::loadFeed)
    }

    suspend fun updateFeed(identifier: String) = getFeed(identifier)?.run { updateFeed(this) }

    private suspend fun updateFeed(feed: FeedInfo) {

        if(BlueskyRecheck.isPending(feed.did)) return
        val lock = Locks.Bluesky[feed.did]
        if(!lock.tryLock()) {
            LOG.debug("Skipping Bluesky feed pull for ${feed.handle}")
            return
        }

        try {

            val posts = when(val userFeed = BlueskyParser.getFeed(feed.did)) {
                is Ok -> userFeed.value.feed.reversed()
                is Err -> when(userFeed.value) {
                    is TrackerErr.NotFound -> {
                        propagateTransaction {
                            LOG.info("Untracking Bluesky feed: ${feed.did}/${feed.handle} as the profile no longer exists.")
                            feed.dbFeed.delete()
                        }
                        return
                    }
                    else -> throw IOException(userFeed.value.toString())
                }
            }

            posts.forEach { post ->
                handlePost(feed, post)
            }

            val sample = posts.firstOrNull { post -> post.post.author.did == feed.did }
            if(sample != null) {
                val handle = sample.post.author.handle
                val displayName = sample.post.author.displayName
                if(handle == "handle.invalid") {
                    LOG.warn("Bluesky handle reporting invalid: ${feed.handle} -> $handle")
                }
                if(feed.handle != handle) {
                    LOG.info("Updating Bluesky handle: ${feed.handle} -> $handle")
                    propagateTransaction {
                        feed.dbFeed.handle = handle
                    }
                }
                if(feed.displayName != displayName) {
                    propagateTransaction {
                        feed.dbFeed.displayName = displayName ?: feed.handle
                    }
                }
            }
        } catch(e: Exception) {
            LOG.warn("Error getting Bluesky feed ${feed.handle}: ${e.message}")
            LOG.debug(e.stackTraceString)
        } finally {
            if(lock.isLocked) lock.unlock()
        }
    }

    suspend fun handleFirstPartyPost(identifier: String, post: BlueskyFeedPost) = getFeed(identifier)?.run {
        handlePost(
            feed = this,
            post = BlueskyPost(
                post = post,
                reply = null,
                reason = null
            )
        )
    }

    /**
     * Handles a Bluesky post that may need notifications sent.
     * Can call for all posts, will reject submissions which are older than known posts.
     */
    private suspend fun handlePost(feed: FeedInfo, post: BlueskyPost) {
        // Filter post
        // Only take latest posts from feed
        val postTimestamp = if(post.isRepost) post.reason!!.indexedAt else post.post.record.createdAt
        if(feed.lastUpdated != null && feed.lastUpdated.javaInstant >= postTimestamp) {
            return
        }
        // Do not take old posts, even if they were missed by the bot
        val age = Duration.between(postTimestamp, Instant.now())
        if(age > Duration.ofHours(2)) {
            return
        }

        if(feed.lastUpdated == null || postTimestamp > feed.lastUpdated.javaInstant) {
            propagateTransaction {
                feed.dbFeed.lastPulledTime = postTimestamp.jodaDateTime
            }
        }

        // Can now notify channels with this post
        discordTask(30_000L) {
            // Check for youtube video info in post
            val text = post.post.record.text
            URLUtil.genericUrl
                .findAll(text)
                .forEach { match ->
                    YoutubeVideoIntake.intakeVideosFromText(match.value)
                }

            val targets = getActiveTargets(feed.dbParent)?.ifEmpty { null }
                ?: return@discordTask // feed untracked entirely or no target channels are currently enabled

            val translationCache = mutableMapOf<String, TranslationResult>()

            targets.forEach target@{ target ->
                val fbk = instances[target.discordClient]
                val discord = fbk.client
                try {
                    val channel = discord.getChannelById(target.discordChannel)
                        .ofType(MessageChannel::class.java)
                        .awaitSingle()

                    val (_, features) = GuildConfigurations.findFeatures(target.discordClient, target.discordGuild?.asLong(), target.discordChannel.asLong())
                    val postCfg = features?.postsSettings ?: PostsSettings()

                    if(!post.notifyOption(postCfg)) return@target

                    // image or video will count as 'media' for the purposes of FBK's mediaOnly flag
                    // determine them separately as non-image embeds will receive a notice in the footer
                    val images = (post.post.embed as? BlueskyEmbedImagesView)?.images
                    val embedImage = images?.first()?.thumb
                    val embedVideo = (post.post.embed as? BlueskyEmbedVideoView)?.thumbnail
                    val embedExternal = (post.post.embed as? BlueskyEmbedExternalView)?.external?.thumb

                    // Translation phase
                    val tlCfg = GuildConfigurations.getOrCreateGuild(fbk.clientId, target.discordGuild!!.asLong()).translator
                    val translation = translatePost(text, post.isRepost, feed.handle, targets, tlCfg, postCfg, translationCache)

                    // Roles phase
                    val mention = getMentionRoleFor(target, channel, postCfg, post.mentionOption)
                    val outdated = Duration.between(postTimestamp, Instant.now()) > Duration.ofMinutes(15)
                    val mentionText = mention?.toText(!outdated, feed.displayName, postTimestamp, feed.did, post.url) ?: ""

                    // Extract post information
                    val quotedPost = post.post.embed
                        ?.run { this as? BlueskyEmbedRecordView }
                        ?.run { record as BlueskyEmbedViewRecord }
                    val event = when {
                        post.isRepost -> "reposted **@${post.post.author.handle}** \uD83D\uDD01"
                        post.isReply -> "replied to **@${(post.reply?.parent as BlueskyPostView).author.handle}** \uD83D\uDCAC"
                        post.isQuote -> "quoted a post from **@${quotedPost?.author?.handle}** \uD83D\uDDE8"
                        else -> "made a new post"
                    }
                    val timestamp = TimestampFormat.RELATIVE_TIME.format(postTimestamp)
                    val action = "**${feed.displayName}** $event $timestamp: ${post.url}"

                    val author = post.post.author
                    val color = mention?.db?.embedColor ?: 686847
                    val postText = text.escapeMarkdown()

                    val notifSpec = when {
                        postCfg.mediaOnly && embedImage == null && embedVideo == null -> return@target
                        postCfg.useComponents -> {
                            // Use newer Discord components v2 to generate message
                            val media = images?.map(BlueskyImage::fullSize)
                                ?: embedVideo?.run(::listOf)
                                ?: embedExternal?.run(::listOf)
                                ?: listOf()
                            val container = Container.of(
                                Color.of(color),
                                listOfNotNull(
                                    if(author.avatar == null) TextDisplay.of(action)
                                    else Section.of(
                                        Thumbnail.of(
                                            UnfurledMediaItem.of(author.avatar)
                                        ),
                                        TextDisplay.of(action)
                                    ),

                                    TextDisplay.of(postText),

                                    if(media.any()) MediaGallery.of(
                                        media.map(UnfurledMediaItem::of).map(MediaGalleryItem::of)
                                    ) else null,

                                    if(translation != null) {
                                        val tlText = StringEscapeUtils.unescapeHtml4(translation.translatedText)
                                        TextDisplay.of("**Post Translation** (${translation.service.fullName}, ${translation.originalLanguage.tag} -> ${translation.targetLanguage.tag})\n$tlText")
                                    } else null,

                                    if(post.isQuote) Section.of(
                                        Button.link(quotedPost?.url ?: "", "View on Bluesky"),
                                        TextDisplay.of("Post Quoted:")
                                    ) else null,

                                    if(mentionText.isNotBlank()) TextDisplay.of(mentionText)
                                    else null,

                                    if(outdated && mention != null) {
                                        LOG.info("Missed ping: $post")
                                        TextDisplay.of("-# Ping was skipped for this late post.")
                                    } else null
                                )
                            )

                            MessageCreateSpec.create()
                                .withFlags(Message.Flag.IS_COMPONENTS_V2)
                                .withComponents(container)
                        }
                        else -> {
                            // Use classic Discord embed style
                            val footer = StringBuilder()
                            val imageCount = images?.size ?: 0
                            if(imageCount > 1) footer.appendLine("(this post contains $imageCount images)")
                            if(embedVideo != null) footer.appendLine("(this post contains a video)")
                            if(embedExternal != null) footer.appendLine("(this post contains embedded content)")

                            MessageCreateSpec.create()
                                .withContent("$mentionText$action")
                                .run { // Create notification embed
                                    val embed = Embeds.other(postText, Color.of(color))
                                        .withAuthor(EmbedCreateFields.Author.of("@${author.handle}", author.permaUrl, author.avatar))
                                        .run { // Add 'fields' to some embeds
                                            val fields = mutableListOf<EmbedCreateFields.Field>()

                                            if(post.isQuote) {
                                                fields.add(EmbedCreateFields.Field.of("**Post Quoted**", quotedPost?.url ?: "", false))
                                            }

                                            if(translation != null) {
                                                val tlText = StringUtils.abbreviate(StringEscapeUtils.unescapeHtml4(translation.translatedText), MagicNumbers.Embed.FIELD.VALUE)
                                                footer.append("Translator: ${translation.service.fullName}, ${translation.originalLanguage.tag} -> ${translation.targetLanguage.tag}\n")
                                                fields.add(EmbedCreateFields.Field.of("**Post Translation**", tlText, false))
                                            }

                                            if(fields.isNotEmpty()) withFields(fields) else this
                                        }
                                        .run { // Add footer to embed
                                            if(outdated && mention != null) {
                                                footer.appendLine("Skipping ping for old post.")
                                                LOG.info("Missed ping: $post")
                                            }
                                            withFooter(EmbedCreateFields.Footer.of(footer.toString().ifBlank { "Bluesky" }, NettyFileServer.blueskyLogo))
                                        }
                                        .run { // Add image to embed
                                            val image = embedImage ?: embedVideo ?: embedExternal
                                            if(image != null) withImage(image) else this
                                        }

                                    withEmbeds(embed)
                                }
                        }
                    }

                    val notif = channel
                        .createMessage(notifSpec)
                        .timeout(Duration.ofMillis(24_000L))
                        .awaitSingle()

                    TrackerUtil.checkAndPublish(fbk, notif)

                } catch(time: TimeoutCancellationException) {
                    LOG.warn("Timeout sending ${target.username} post to ${target.discordClient}/${target.discordGuild?.asString()}/${target.discordChannel.asString()}")
                } catch(e: Exception) {
                    if(e is ClientException && e.status.code() == 403) {
                        TrackerUtil.permissionDenied(fbk, target.discordGuild, target.discordChannel, FeatureChannel::postsTargetChannel) { propagateTransaction { target.findDbTarget().delete() } }
                        LOG.warn("Unable to send post to channel '${target.discordClient}/${target.discordGuild?.asString()}/${target.discordChannel.asString()}'. Disabling feature in channel. Action by: BlueskyChecker")
                    } else {
                        LOG.warn("Error sending post to channel ${target.discordClient}/${target.discordGuild?.asString()}/${target.discordChannel.asString()}: ${e.message}")
                        LOG.debug(e.stackTraceString)
                    }
                }
            }
        }
    }

}
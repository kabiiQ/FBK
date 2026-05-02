package moe.kabii.trackers.posts.holoplus.watcher

import discord4j.common.util.TimestampFormat
import discord4j.core.`object`.component.*
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.MessageCreateFields
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.mongodb.guilds.PostsSettings
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.data.relational.posts.holoplus.HoloplusFeed
import moe.kabii.instances.DiscordInstances
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.posts.PostWatcher
import moe.kabii.trackers.posts.holoplus.parser.HoloplusParser
import moe.kabii.util.constants.Opcode
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.*
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.Duration
import java.time.Instant

class HoloplusChecker(val cooldowns: ServiceRequestCooldownSpec, instances: DiscordInstances): Runnable, PostWatcher(instances) {

    override fun run() {
        applicationLoop {
            try {
                delay(Duration.ofMinutes(1))
                checkGeneralFeed()
            } catch(e: Exception) {
                LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }

    data class FeedInfo(
        val dbFeed: HoloplusFeed,
        val dbParent: TrackedSocialFeeds.SocialFeed,
        val talentId: String,
        val talentName: String,
        val lastKnownThread: Instant
    )

    @RequiresExposedContext
    fun loadFeed(feed: HoloplusFeed) = FeedInfo(
        feed,
        feed.feed,
        feed.talentId,
        feed.talentName,
        feed.lastKnownThread.javaInstant
    )

    private suspend fun checkGeneralFeed() {
        // Check the primary feed for posts associated to each talent
        val posts = when(val feed = HoloplusParser.getFeed()) {
            is Ok -> {
                feed.value.items.associateBy { item -> item.talent.id }
            }
            else -> {
                LOG.warn("Error getting Holoplus post feed")
                return
            }
        }
        // Get all tracked talents
        val tracked = propagateTransaction {
            HoloplusFeed
                .all()
                .map(::loadFeed)
                .toList()
        }

        // Check each tracked feed for updates from the general feed
        tracked.forEach { feed ->
            val recent = posts[feed.talentId]
            if(recent != null && recent.thread.createdAt > feed.lastKnownThread) {
                updateTalentFeed(feed, recent.channelId)
            }
        }
    }

    private suspend fun updateTalentFeed(feed: FeedInfo, channelId: String) {
        // Get the feed for a specific talent and post
        val holoFeed = when(val channel = HoloplusParser.getTalentChannel(channelId)) {
            is Ok -> channel.value
            is Err -> {
                LOG.warn("Error getting Holoplus talent feed ${feed.talentName}:${feed.talentId} / $channelId")
                return
            }
        }
        holoFeed.items.reversed().forEach { post ->
            val talent = HoloplusParser.talents.getValue(feed.talentId)
            if(post.createdAt <= feed.lastKnownThread) return@forEach // Previously posted
            val age = Duration.between(post.createdAt, Instant.now())
            if(age > Duration.ofHours(2)) return // Old posts
            if(post.createdAt > feed.lastKnownThread) {
                propagateTransaction {
                    feed.dbFeed.lastKnownThread = post.createdAt.jodaDateTime
                }
            }

            // Notify any following channels about new post
            discordTask(30_000L) {
                val targets = getActiveTargets(feed.dbParent)
                    ?.ifEmpty { null }
                    ?: return@discordTask

                val timestamp = TimestampFormat.RELATIVE_TIME.format(post.createdAt)
                val action = "**${talent.name}** posted $timestamp: ${post.url}"
                val body = StringUtils.abbreviate(post.body.escapeMarkdown(), 2000)

                val voiceClip = post.voiceClip?.let { clip ->
                    try {
                        val bytes = ByteArrayOutputStream()
                        val stream = URI(clip.url).toURL().openStream()
                        IOUtils.copy(stream, bytes)
                        bytes.toByteArray()
                    } catch(e: Exception) {
                        LOG.warn("Error fetching Holoplus voice clip: ${e.message} :: $post")
                        LOG.debug(e.stackTraceString)
                        null
                    }
                }

                targets.forEach { target ->

                    val fbk = instances[target.discordClient]
                    val discord = fbk.client
                    try {
                        val channel = discord.getChannelById(target.discordChannel)
                            .ofType(MessageChannel::class.java)
                            .awaitSingle()
                        val (_, features) = GuildConfigurations.findFeatures(target.discordClient, target.discordGuild?.asLong(), target.discordChannel.asLong())
                        val postCfg = features?.postsSettings ?: PostsSettings()

                        // Roles phase
                        val mention = getMentionRoleFor(target, channel, postCfg, PostsSettings::mentionNormalPosts)
                        val mentionText = mention?.toText(includeRole = true, displayName = feed.talentName, post.createdAt, feed.talentId,
                            URLUtil.Holoplus.feed(channelId)) ?: ""
                        val color = mention?.db?.embedColor?.run(Color::of) ?: HoloplusParser.color

                        // Always use components to generate notification post
                        val container = Container.of(
                            color,
                            listOfNotNull(
                                Section.of(
                                    Thumbnail.of(
                                        UnfurledMediaItem.of(NettyFileServer.holoplusLogo)
                                    ),
                                    TextDisplay.of(action)
                                ),

                                TextDisplay.of("## ${post.title}"),
                                TextDisplay.of(body),

                                if(post.imageUrls.any()) MediaGallery.of(
                                    post.imageUrls.map(UnfurledMediaItem::of).map(MediaGalleryItem::of)
                                ) else null,

                                if(post.translations != null && post.language != "en") {
                                    val translation = post.translations.en
                                    Section.of(
                                        Thumbnail.of(
                                            UnfurledMediaItem.of(NettyFileServer.holoplusTranslation)
                                        ),
                                        TextDisplay.of("## ${translation.title}\n${translation.body}")
                                    )
                                } else null,

                                if(mentionText.isNotBlank()) TextDisplay.of(mentionText)
                                else null
                            )
                        )

                        val spec = MessageCreateSpec.create()
                            .withFlags(Message.Flag.IS_COMPONENTS_V2)
                            .withComponents(container)

                        val notif = channel
                            .createMessage(spec)
                            .timeout(Duration.ofMillis(12_000L))
                            .awaitSingle()

                        TrackerUtil.checkAndPublish(fbk, notif)

                        // Send voice clip as seperate message so that it is playable - Discord limitation
                        if(voiceClip != null) {
                            val voice = ByteArrayInputStream(voiceClip)
                            channel
                                .createMessage()
                                .withFiles(MessageCreateFields.File.of("voice.m4a", voice))
                                .withMessageReference(notif.id.reference)
                                .timeout(Duration.ofMillis(12_000L))
                                .awaitSingle()
                        }
                    } catch(_: TimeoutCancellationException) {
                        LOG.warn("Timeout sending ${target.username} post to ${target.discordClient}/${target.discordGuild?.asString()}/${target.discordChannel.asString()}")
                    } catch(e: Exception) {
                        if(e is ClientException && Opcode.denied(e.opcode)) {
                            TrackerUtil.permissionDenied(fbk, target.discordGuild, target.discordChannel, FeatureChannel::postsTargetChannel) { target.findDbTarget().delete() }
                            LOG.warn("Unable to send post to channel '${target.discordClient}/${target.discordGuild?.asString()}/${target.discordChannel.asString()}'. Disabling feature in channel. Action by: HoloplusChecker")
                        } else {
                            LOG.warn("Error sending post to channel ${target.discordClient}/${target.discordGuild?.asString()}/${target.discordChannel.asString()}: ${e.message}")
                            LOG.debug(e.stackTraceString)
                        }
                    }
                }
            }
        }
    }
}
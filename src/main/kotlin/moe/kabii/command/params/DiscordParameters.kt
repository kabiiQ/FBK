package moe.kabii.command.params

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.kabii.command.Command
import moe.kabii.command.GuildTargetInvalidException
import moe.kabii.command.channelVerify
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.discord.conversation.*
import moe.kabii.discord.event.message.MessageHandler
import moe.kabii.discord.util.errorColor
import moe.kabii.discord.util.fbkColor
import moe.kabii.discord.util.specColor
import moe.kabii.util.constants.EmojiCharacters
import moe.kabii.util.extensions.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration

data class DiscordParameters (
    val handler: MessageHandler,
    val event: MessageCreateEvent,
    val chan: MessageChannel,
    val guild: Guild?,
    val author: User,
    val noCmd: String,
    val args: List<String>,
    val command: Command,
    val alias: String) {

    // javac thinks that guild could be null after non-null check?
    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    val target: Guild by lazy {
        if(guild != null) return@lazy guild!!
        val user = transaction {
            DiscordObjects.User.getOrInsert(author.id.asLong())
        }
        val userTarget = user.target
        if (userTarget != null) {
            val dGuild = event.client.getGuildById(userTarget.snowflake).tryBlock().orNull()
            if(dGuild != null) return@lazy dGuild!! else {
                user.target = null
                throw GuildTargetInvalidException("Saved server **$userTarget** is no longer valid.")
            }
        } else throw GuildTargetInvalidException("Guild context unknown.")
    }

    val isPM = guild == null

    val member: Member by lazy {
        target.getMemberById(author.id).tryBlock().orNull() ?: throw GuildTargetInvalidException("**${author.username}** is not a member of **${target.name}**.")
    }

    val config: GuildConfiguration by lazy {
        GuildConfigurations.getOrCreateGuild(target.id.asLong())
    }

    suspend fun features() = config.getOrCreateFeatures(guildChan.id.asLong())

    // error if we need to verify channel permissions for targeting specific channel, but this was executed in DMs
    val guildChan: GuildChannel
        get() = (chan as? GuildChannel) ?: throw GuildTargetInvalidException("This command must be executed in a server's channel.")

    suspend fun channelVerify(vararg permissions: Permission) = member.channelVerify(guildChan, *permissions)

    fun error(block: EmbedSuspension) = chan.createEmbed { embed ->
        errorColor(embed)
        runBlocking {
            block(embed)
        }
    }

    fun error(author: User, block: EmbedSuspension) = this.error {
        block(this)
        setAuthor(author.userAddress(), null, author.avatarUrl)
    }

    fun embed(block: EmbedSuspension) = chan.createEmbed { embed ->
        fbkColor(embed)
        runBlocking {
            block(embed)
        }
    }

    fun embed(author: User, block: EmbedSuspension) = this.embed {
        block(this)
        setAuthor(author.userAddress(), null, author.avatarUrl)
    }

    fun embedBlock(block: EmbedBlock) = chan.createEmbed { embed ->
        fbkColor(embed)
        block(embed)
    }

    fun usage(commandError: String, linkText: String?) = chan.createEmbed { embed ->
        specColor(embed)
        val link = if(linkText != null) {
            if(command.wikiPath != null) " Command usage: **[$linkText](${command.getHelpURL()})**." else " Command usage: **$linkText**."
        } else ""
        embed.setDescription("$commandError$link")
    }

    fun error(error: String) = error { setDescription(error) }
    fun error(user: User, error: String) = error(user) { setDescription(error) }
    fun embed(info: String) = embed { setDescription(info) }
    fun embed(user: User, info: String) = embed(user) { setDescription(info) }

    suspend fun getMessage(limitDifferentUser: Long?=null, timeout: Long? = 40000) = suspendCancellableCoroutine<Message?> {
        var (user, channel) = Criteria defaultFor this
        user = limitDifferentUser ?: user
        val responseCriteria = ResponseCriteria(user, channel, ResponseType.MESSAGE)
        Conversation.register(responseCriteria, event.client, it, timeout = timeout)
    }

    suspend fun getString(limitDifferentUser: Long?=null, timeout: Long? = 40000) = suspendCancellableCoroutine<String?> {
        var (user, channel) = Criteria defaultFor this
        user = limitDifferentUser ?: user
        val responseCriteria = ResponseCriteria(user, channel, ResponseType.STR)
        Conversation.register(responseCriteria, event.client, it, timeout = timeout)
    }

    suspend fun getLine(limitDifferentUser: Long?=null) = suspendCancellableCoroutine<String?> {
        var (user, channel) = Criteria defaultFor this
        user = limitDifferentUser ?: user
        val responseCriteria = ResponseCriteria(user, channel, ResponseType.LINE)
        Conversation.register(responseCriteria, event.client, it)
    }

    suspend fun getDouble(range: ClosedRange<Double>? = null, timeout: Long? = 40000) = suspendCancellableCoroutine<Double?> {
        val (user, channel) = Criteria defaultFor this
        val responseCriteria = DoubleResponseCriteria(user, channel, range)
        Conversation.register(responseCriteria, event.client, it, timeout = timeout)
    }

    suspend fun getBool(react: Message?=null, timeout: Long? = null, add: Boolean = true, limitDifferentUser: Long? = null) = suspendCancellableCoroutine<Boolean?> {
        var (user, channel) = Criteria defaultFor this
        user = limitDifferentUser ?: user
        val responseCriteria = BoolResponseCriteria(user, channel, react?.id?.asLong())
        val reactionListener = react?.run {
            ReactionListener(
                MessageInfo.of(react),
                listOf(
                    ReactionInfo(EmojiCharacters.yes, "yes"),
                    ReactionInfo(EmojiCharacters.no, "no")
                ),
                user,
                event.client
            ) { info, _, conversation ->
                // callback when user reacts with an emoji, pass as text response - no distinction needed
                // conversation will exist since we know this reaction was created in the context of a conversation
                conversation?.test(info.name)
                false
            }.apply { create(react, add) }
        }
        Conversation.register(responseCriteria, event.client, it, reactionListener, timeout = timeout)
    }

    suspend fun getLong(range: LongRange?=null, message: Message?=null, addReactions: Boolean = false, timeout: Long? = 40000, add: Boolean = true) = suspendCancellableCoroutine<Long?> {
        val (user, channel) = Criteria defaultFor this
        val responseCriteria = LongResponseCriteria(user, channel, range, message?.id?.asLong())
        val reactionListener = if(addReactions && message != null) {
            if (range != null && range.first >= 0 && range.last <= 10) {
                ReactionListener(
                    MessageInfo.of(message),
                    range.map { int -> ReactionInfo("$int\u20E3", int.toString()) }.toList(),
                    user,
                    event.client) { info, _, conversation ->
                    conversation?.test(info.name)
                    true
                }.apply { create(message, add) }
            } else {
                throw IllegalArgumentException("Message provided for reactions but range is outside emoji range")
            }
        } else null
        Conversation.register(responseCriteria, event.client, it, reactionListener, timeout)
    }

    suspend fun getPage(page: Page, reactOn: Message, add: Boolean = true) = suspendCancellableCoroutine<Page?> { coroutine ->
        val (user, channel) = Criteria defaultFor this
        val responseCriteria = PageResponseCriteria(user, channel, reactOn.id.asLong(), page)
        val reactionListener = reactOn.run {
            ReactionListener(
                MessageInfo.of(this),
                Direction.reactions,
                user,
                event.client
            ) { response, _, conversation ->
                conversation?.test(response.name)
                false
            }.apply { create(reactOn, add) }
        }
        Conversation.register(responseCriteria, event.client, coroutine, reactionListener, 30000)
    }

    suspend fun getDuration(timeout: Long? = 40000) = suspendCancellableCoroutine<Duration?> {
        val (user, channel) = Criteria defaultFor this
        val responseCriteria = ResponseCriteria(user, channel, ResponseType.DURATION)
        Conversation.register(responseCriteria, event.client, it, timeout = timeout)
    }
}
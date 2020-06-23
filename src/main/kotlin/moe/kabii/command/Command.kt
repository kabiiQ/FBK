package moe.kabii.command

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import discord4j.core.`object`.entity.*
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import discord4j.rest.util.Permission
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.data.relational.DiscordObjects
import moe.kabii.discord.conversation.*
import moe.kabii.discord.event.bot.MessageHandler
import moe.kabii.discord.util.RoleUtil
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.EmbedReceiver
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import moe.kabii.util.EmojiCharacters
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.core.publisher.Mono

// Now purely aesthetic, Command inheritance is reflectively searched
interface CommandContainer

abstract class Command(val baseName: String, vararg alias: String) {
    val aliases = listOf(baseName, *alias)

    open val helpURL: String? = null // TODO make this abstract once docs are available
    open val commandExempt: Boolean = false

    val sourceRoot = "https://github.com/kabiiQ/FBK/src/main/kotlin"

    var executeDiscord: (suspend (DiscordParameters) -> Unit)? = null
    private set
    var executeTwitch: (suspend (TwitchParameters) -> Unit)? = null
    private set

    var discordReqs: List<Permission> = listOf(
            Permission.SEND_MESSAGES,
            Permission.EMBED_LINKS
        )

    fun botReqs(vararg permission: Permission) {
        discordReqs = discordReqs + permission.toList()
    }

    fun discord(block: suspend DiscordParameters.() -> Unit) {
        executeDiscord = block
    }

    fun twitch(block: suspend TwitchParameters.() -> Unit) {
        executeTwitch = block
    }
}

fun errorColor(spec: EmbedCreateSpec) = spec.setColor(Color.RED)
fun fbkColor(spec: EmbedCreateSpec) = spec.setColor(Color.of(12187102))
fun specColor(spec: EmbedCreateSpec) = spec.setColor(Color.of(13369088))
fun reminderColor(spec: EmbedCreateSpec) = spec.setColor(Color.of(44031))
fun logColor(member: Member?, spec: EmbedCreateSpec) =
    Mono.justOrEmpty(member)
        .flatMap { m -> RoleUtil.getColorRole(m!!) } // weird type interaction means this is Member? but it will never be null inside the operators
        .map(Role::getColor)
        .map(spec::setColor)
        .defaultIfEmpty(fbkColor(spec))

data class DiscordParameters (
    val handler: MessageHandler,
    val event: MessageCreateEvent,
    val chan: MessageChannel,
    val guild: Guild?,
    val author: User,
    val isPM: Boolean,
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

    val member: Member by lazy {
        target.getMemberById(author.id).tryBlock().orNull() ?: throw GuildTargetInvalidException("**${author.username}** is not a member of **${target.name}**.")
    }

    val config: GuildConfiguration by lazy {
        GuildConfigurations.getOrCreateGuild(target.id.asLong())
    }

    // error if we need to verify channel permissions for targeting specific channel, but this was executed in DMs
    val guildChan: GuildChannel
    get() = (chan as? GuildChannel) ?: throw GuildTargetInvalidException("This command must be executed in a server's channel.")

    suspend fun channelVerify(vararg permissions: Permission) = member.channelVerify(guildChan, *permissions)

    fun error(block: EmbedReceiver) = chan.createEmbed { embed ->
        errorColor(embed)
        runBlocking {
            block(embed)
        }
    }

    fun embed(block: EmbedReceiver) = chan.createEmbed { embed ->
        fbkColor(embed)
        runBlocking {
            block(embed)
        }
    }

    fun embedBlock(block: EmbedBlock) = chan.createEmbed { embed ->
        fbkColor(embed)
        runBlocking {
            block(embed)
        }
    }

    fun usage(commandError: String, linkText: String?) = chan.createEmbed { embed ->
        specColor(embed)
        val link = if(linkText != null) {
            if(command.helpURL != null) " Command usage: **[$linkText](${command.helpURL})**." else " Command usage: **$linkText**."
        } else ""
        embed.setDescription("$commandError$link")
    }

    fun error(error: String) = error { setDescription(error) }
    fun embed(info: String) = embed { setDescription(info) }

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

    suspend fun getBool(react: Message?=null, timeout: Long? = null, add: Boolean = true) = suspendCancellableCoroutine<Boolean?> {
        val (user, channel) = Criteria defaultFor this
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
}

class GuildTargetInvalidException(val string: String) : RuntimeException()
class FeatureDisabledException(val feature: String, val origin: DiscordParameters) : RuntimeException()

class TwitchParameters (
    val event: ChannelMessageEvent,
    val noCmd: String,
    val guild: GuildConfiguration?,
    val isMod: Boolean,
    val args: List<String>)
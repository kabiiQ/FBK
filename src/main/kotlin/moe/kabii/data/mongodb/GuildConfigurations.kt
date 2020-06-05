package moe.kabii.data.mongodb

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.reaction.ReactionEmoji
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.command.Command
import moe.kabii.structure.GuildID
import moe.kabii.util.DiscordEmoji
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.updateOne
import org.litote.kmongo.newId
import reactor.core.publisher.DirectProcessor
import reactor.core.scheduler.Schedulers
import java.util.concurrent.ConcurrentHashMap

object GuildConfigurations {
    val mongoConfigurations = MongoDBConnection.mongoDB.getCollection<GuildConfiguration>()
    val guildConfigurations: MutableMap<GuildID, GuildConfiguration>

    init {
        // grab existing guild configurations
        guildConfigurations = runBlocking {
            mongoConfigurations.find().toList()
                .associateBy(GuildConfiguration::guildid)
                .run { ConcurrentHashMap(this) }
        }
    }

    @Synchronized fun getOrCreateGuild(id: Long) = guildConfigurations.getOrPut(id) { GuildConfiguration(guildid = id) }
    @Synchronized fun getGuildForTwitch(twitchID: Long) = guildConfigurations.asSequence().find { it.value.options.linkedTwitchChannel?.twitchid == twitchID }?.value
}

// per guild - guildconfiguration collection
data class GuildConfiguration(
    val _id: Id<GuildConfiguration> = newId(),
    val guildid: Long,
    var prefix: String = ";",
    var suffix: String? = "desu",
    val options: OptionalFeatures = OptionalFeatures(),
    val commands: DummyCommands = DummyCommands(),
    val autoRoles: AutoRoles = AutoRoles(),
    val selfRoles: SelfRoles = SelfRoles(),
    val guildSettings: GuildSettings = GuildSettings(),
    val tempVoiceChannels: TempChannels = TempChannels(),
    val commandFilter: CommandFilter = CommandFilter(),
    val musicBot: MusicSettings = MusicSettings(),
    val userLog: UserLog = UserLog()) {

    fun logChannels() = options.featureChannels.values.toList()
        .filter(FeatureChannel::logChannel)

    fun save() {
        queue.next(this)
    }

    fun getOrCreateFeatures(channel: Long): FeatureChannel = options.featureChannels.getOrPut(channel) {
        FeatureChannel(channel).also { save() }
    }

    companion object {
        private val configProcessor = DirectProcessor.create<GuildConfiguration>()
        private val queue = configProcessor.sink()

        init {
            configProcessor
                .subscribeOn(Schedulers.newSingle("ConfigProcessor"))
                .flatMap { config ->
                    mono {
                        GuildConfigurations.mongoConfigurations.updateOne(config, upsert)
                    }
                }.subscribe()
        }
    }
}

data class OptionalFeatures(
    val featureChannels: MutableMap<Long, FeatureChannel> = mutableMapOf(),
    var linkedTwitchChannel: TwitchConfig? = null)

data class FeatureChannel(
    val channelID: Long,
    var twitchChannel: Boolean = false,
    var animeChannel: Boolean = false,
    var logChannel: Boolean = false,
    var musicChannel: Boolean = false,
    var tempChannelCreation: Boolean = false,
    val logSettings: LogSettings = LogSettings(channelID),
    val featureSettings: FeatureSettings = FeatureSettings()
) {
    fun anyEnabled() = booleanArrayOf(twitchChannel, animeChannel, logChannel).any(true::equals)
}

data class FeatureSettings(
    var streamSummaries: Boolean = true,
    var streamThumbnails: Boolean = true,
    var streamViewersSummary: Boolean = true,
    var streamEndTitle: Boolean = true,
    var streamEndGame: Boolean = true,
    var mediaNewItem: Boolean = true,
    var mediaStatusChange: Boolean = true,
    var mediaUpdatedStatus: Boolean = true
)

data class TwitchConfig(
        var twitchid: Long,
        var urlTitles: Boolean = true)

data class LogSettings(
    val channelID: Long,
    var includeBots: Boolean = true,
    // components a modlog channel can have
    var joinLog: Boolean = false,
    var joinFormat: String = defaultJoin,
    var partLog: Boolean = false,
    var partFormat: String = defaultPart,
    var avatarLog: Boolean = false,
    var usernameLog: Boolean = false,
    var voiceLog: Boolean = false,
    var editLog: Boolean = false,
    var deleteLog: Boolean = false,
    var roleUpdateLog: Boolean = false) {

    fun shouldInclude(user: User): Boolean = includeBots || !user.isBot

    companion object {
        const val defaultJoin = "**&name&discrim** joined the server. (&mention)&new"
        const val defaultPart = "**&name&discrim** left the server. (&mention)"
    }

    fun anyEnabled() = booleanArrayOf(joinLog, partLog, avatarLog, usernameLog, voiceLog, editLog, deleteLog).any(true::equals)
}

data class DummyCommands(
    val commands: MutableList<DummyCommand> = mutableListOf()) {

    fun remove(command: String) = commands.removeIf { it.command == command }

    fun insertIsUpdated(command: DummyCommand): Boolean {
        val replacing = commands.removeIf { it.command == command.command }
        commands.add(command)
        return replacing
    }
}

data class DummyCommand(
        val command: String,
        var response: String,
        var restrict: Boolean)

data class AutoRoles(
    val joinConfigurations: MutableList<JoinConfiguration> = mutableListOf(),
    val voiceConfigurations: MutableList<VoiceConfiguration> = mutableListOf(),
    val rejoinRoles: MutableMap<Long, LongArray> = mutableMapOf(),
    val exclusiveRoleSets: MutableList<ExclusiveRoleSet> = mutableListOf()
)

data class JoinConfiguration(
    val inviteTarget: String?,
    val role: Long
)

data class VoiceConfiguration(
    val targetChannel: Long?,
    val role: Long
)

data class ExclusiveRoleSet(
    val name: String,
    val roles: MutableSet<Long> = mutableSetOf()
)

data class SelfRoles(
    val enabledRoles: MutableList<Long> = mutableListOf(),
    val roleCommands: MutableMap<String, Long> = mutableMapOf(),
    val reactionRoles: MutableList<ReactionRoleConfig> = mutableListOf()
)

data class ReactionRoleConfig(
    val message: MessageInfo,
    val reaction: DiscordEmoji,
    val role: Long
)

data class MusicSettings(
    var startingVolume: Int = defaultStartingVolume,
    var autoJoinChannel: Long? = null,
    var lastChannel: Long? = null,
    var deleteOldBotMessages: Boolean = true,
    var deleteUserCommands: Boolean = false,
    var queuerFSkip: Boolean = true,
    var alwaysFSkip: Boolean = false,
    var skipIfAbsent: Boolean = false,
    var skipRatio: Long = defaultRatio,
    var skipUsers: Long = defaultUsers,
    var maxTracksUser: Long = defaultMaxTracksUser,
    var volumeLimit: Long = defaultVolumeLimit,
    var activeQueue: List<QueuedTrack> = listOf()
) {
    companion object {
        const val defaultRatio = 50L
        const val defaultUsers = 4L
        const val defaultStartingVolume = 15
        const val defaultMaxTracksUser = 0L
        const val defaultVolumeLimit = 100L
    }

    // just serializable info that we need to requeue the tracks after a restart
    data class QueuedTrack(
        val uri: String,
        val author_name: String,
        val author: Long,
        val originChannel: Long
    )
}

data class GuildSettings(
    var embedMessages: Boolean = true,
    var followRoles: Boolean = true,
    var reassignRoles: Boolean = false,
    var defaultFollowChannel: TrackedStreams.StreamInfo? = null,
    var twitchURLInfo: Boolean = false
)

data class TempChannels(
    val tempChannels: MutableList<Long> = mutableListOf()
)

// using custom solution rather than something like an enum as this is a database class
data class CommandFilter(
    val blacklist: MutableSet<String> = mutableSetOf(),
    val whitelist: MutableSet<String> = mutableSetOf()
) {
    var whitelisted: Boolean = false
    private set
    var blacklisted: Boolean = true
    private set

    fun useBlacklist() {
        whitelisted = false
        blacklisted = true
    }

    fun useWhitelist() {
        blacklisted = false
        whitelisted = true
    }

    fun isCommandEnabled(command: Command): Boolean {
        // check all aliases against the list in case things got shuffled
        return when {
            // some commands can not be disabled in order to avoid being locked out of the bot
            command.commandExempt -> true
            // normal behavior, some commands might be disabled
            blacklisted -> command.aliases.find { alias -> blacklist.contains(alias) } == null
            // optional behavior, some commands might be enabled
            whitelisted -> command.aliases.find { alias -> whitelist.contains(alias) } != null
            else -> error("Illegal blacklist/whitelist flag configuration")
        }
    }
}

data class UserLog(
    val users: MutableList<GuildMember> = mutableListOf())

// separate data type for GuildMember because it will probably be expanded in the future to log more user info
data class GuildMember(
        var current: Boolean,
        val userID: Long)

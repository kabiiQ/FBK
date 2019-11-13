package moe.kabii.data.mongodb

import discord4j.core.`object`.entity.User
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Transient
import moe.kabii.discord.command.Command
import moe.kabii.structure.GuildID
import moe.kabii.structure.TwitchID
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.updateOne
import org.litote.kmongo.newId
import reactor.core.publisher.DirectProcessor
import reactor.core.scheduler.Schedulers
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

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
    @Synchronized fun getGuildForTwitch(twitchID: Long) = guildConfigurations.asSequence().find { it.value.options.linkedTwitchChannel?.twitchid ?: 0 == twitchID }?.value
}

// per guild - guildconfiguration collection
data class GuildConfiguration(
    val _id: Id<GuildConfiguration> = newId(),
    val guildid: Long,
        // populate all fields
    var prefix: String = ";",
    var suffix: String? = "desu",
    val options: OptionalFeatures = OptionalFeatures(),
    val commands: DummyCommands = DummyCommands(),
    val autoRoles: AutoRoles = AutoRoles(),
    val selfRoles: SelfRoles = SelfRoles(),
    val guildSettings: GuildSettings = GuildSettings(),
    val tempVoiceChannels: TempChannels = TempChannels(),
    val commandFilter: CommandFilter = CommandFilter(),
    val twitchMentionRoles: MutableMap<TwitchID, Long> = mutableMapOf(),
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
                .subscribe { config ->
                    runBlocking {
                        GuildConfigurations.mongoConfigurations.updateOne(config, upsert)
                    }
            }
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
    val logSettings: LogSettings = LogSettings(channelID),
    val featureSettings: FeatureSettings = FeatureSettings()
) {
    fun anyEnabled() = booleanArrayOf(twitchChannel, animeChannel, logChannel).any(true::equals)
}

data class FeatureSettings(
    var streamSummaries: Boolean = true,
    var streamThumbnails: Boolean = true,
    var mediaNewItem: Boolean = true,
    var mediaStatusChange: Boolean = true,
    var mediaUpdatedStatus: Boolean = false
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
    var deleteLog: Boolean = false) {

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
) /*{
    fun insertJoinConfig(config: JoinConfiguration): Long {
        val existing = joinConfigurations.entries.find { (_, v) -> v == config }
        return if(existing != null) existing.key else {
            // insert into this map as the lowest available id for ease of use
            val usedKeys = joinConfigurations.keys.iterator() // this iterates the keys in ascending order of the sortedmap
            var newID = 0L
            for(id in 0..Integer.MAX_VALUE) {
                if(!usedKeys.hasNext() || usedKeys.next() != id.toLong()) { // I think this is fairly efficient? works since both 'sets' are sorted to find the first integer that is not currently in use in the ma
                    newID = id.toLong()
                    break
                }
            }
            joinConfigurations[newID] = config; newID
        }
    }
}*/

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
    val roleMentionMessages: MutableList<ReactionRoleMessage> = mutableListOf()
)

data class ReactionRoleMessage(
    val message: MessageInfo,
    val role: Long
)

data class MusicSettings(
    var volume: Int = defaultVolume,
    var autoJoinChannel: Long? = null,
    var lastChannel: Long? = null,
    var deleteOldBotCommands: Boolean = true,
    var deleteUserCommnads: Boolean = false,
    var queuerFSkip: Boolean = true,
    var alwaysFSkip: Boolean = false,
    var skipIfAbsent: Boolean = false,
    var skipRatio: Long = defaultRatio,
    var skipUsers: Long = defaultUsers,
    var maxTracksUser: Long = defaultMaxTracksUser,
    var adminVolumeLimit: Long = defaultAdminVolumeLimit,
    var activeQueue: List<QueuedTrack> = listOf()
) {
    companion object {
        const val defaultRatio = 50L
        const val defaultUsers = 4L
        const val defaultVolume = 15
        const val defaultMaxTracksUser = 0L
        const val defaultAdminVolumeLimit = 200L
    }

    // just serializable info that we need to requeue the tracks after a restart
    data class QueuedTrack(
        val uri: String,
        val author_name: String,
        val author: Long,
        val originChannel: Long
    )
}

data class UserLog(
    val users: MutableList<GuildMember> = mutableListOf())

data class GuildSettings(
    var embedMessages: Boolean = true,
    var followRoles: Boolean = true,
    var reassignRoles: Boolean = false,
    var defaultFollowChannel: Long? = null,
    var twitchURLInfo: Boolean = false
)

data class TempChannels(
    var tempChannelCategory: Long? = null,
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
            else -> throw IllegalStateException("Illegal blacklist/whitelist flag configuration")
        }
    }
}

// separate data type for GuildMember because it will probably be expanded in the future to log more user info
data class GuildMember(
        var current: Boolean,
        val userID: Long)

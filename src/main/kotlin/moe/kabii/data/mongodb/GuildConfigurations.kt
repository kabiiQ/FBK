package moe.kabii.data.mongodb

import kotlinx.coroutines.runBlocking
import moe.kabii.data.mongodb.guilds.*
import moe.kabii.structure.GuildID
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.updateOne
import org.litote.kmongo.newId
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
    var prefix: String = defaultPrefix,
    var suffix: String? = defaultSuffix,
    val options: OptionalFeatures = OptionalFeatures(),
    val echoCommands: EchoCommands = EchoCommands(),
    val autoRoles: AutoRoles = AutoRoles(),
    val selfRoles: SelfRoles = SelfRoles(),
    val guildSettings: GuildSettings = GuildSettings(),
    val tempVoiceChannels: TempChannels = TempChannels(),
    val commandFilter: CommandFilter = CommandFilter(),
    val musicBot: MusicSettings = MusicSettings(),
    var starboard: StarboardSetup? = null,
    val userLog: UserLog = UserLog()) {

    companion object {
        const val defaultPrefix = ";"
        const val defaultSuffix = "desu"
    }

    fun logChannels() = options.featureChannels.values.toList()
        .filter(FeatureChannel::logChannel)

    suspend fun save() {
        GuildConfigurations.mongoConfigurations.updateOne(this, upsert)
    }

    suspend fun getOrCreateFeatures(channel: Long): FeatureChannel = options.featureChannels.getOrPut(channel) {
        FeatureChannel(channel).also { save() }
    }

    suspend fun removeSelf() {
        GuildConfigurations.guildConfigurations.remove(guildid)
        GuildConfigurations.mongoConfigurations.deleteOneById(this._id)
    }
}

// todo should be sql data
data class UserLog(
    val users: MutableList<GuildMember> = mutableListOf())

// separate data type for GuildMember because it will probably be expanded in the future to log more user info
data class GuildMember(
        var current: Boolean,
        val userID: Long)

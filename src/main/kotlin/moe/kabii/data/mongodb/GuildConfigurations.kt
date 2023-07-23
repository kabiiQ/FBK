package moe.kabii.data.mongodb

import kotlinx.coroutines.runBlocking
import moe.kabii.data.mongodb.guilds.*
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.updateOne
import org.litote.kmongo.newId
import java.util.concurrent.ConcurrentHashMap

data class GuildTarget(val clientId: Int, val guildId: Long)

object GuildConfigurations {
    val mongoConfigurations = MongoDBConnection.mongoDB.getCollection<GuildConfiguration>()
    val guildConfigurations: MutableMap<GuildTarget, GuildConfiguration>

    init {
        // grab existing guild configurations
        guildConfigurations = runBlocking {
            mongoConfigurations.find().toList()
                .associateBy { config -> GuildTarget(config.guildClientId, config.guildid) }
                .run { ConcurrentHashMap(this) }
        }
    }

    @Synchronized fun getOrCreateGuild(clientId: Int, id: Long) = guildConfigurations.getOrPut(GuildTarget(clientId, id)) { GuildConfiguration(guildid = id, guildClientId = clientId) }

    suspend fun findFeatures(clientId: Int, guildId: Long?, channelId: Long?): Pair<GuildConfiguration?, FeatureChannel?> {
        if(guildId == null || channelId == null) return null to null
        val guildConfig = getOrCreateGuild(clientId, guildId)
        return guildConfig to guildConfig.getOrCreateFeatures(channelId)
    }
}

// per guild - guildconfiguration collection
data class GuildConfiguration(
    val _id: Id<GuildConfiguration> = newId(),
    val guildid: Long,
    val options: OptionalFeatures = OptionalFeatures(),
    val guildCustomCommands: CustomCommands = CustomCommands(),
    val autoRoles: AutoRoles = AutoRoles(),
    val guildSettings: GuildSettings = GuildSettings(),
    val tempVoiceChannels: TempChannels = TempChannels(),
    val musicBot: MusicSettings = MusicSettings(),
    val translator: TranslatorSettings = TranslatorSettings(),
    val guildClientId: Int,

    var starboardSetup: StarboardSetup = StarboardSetup(),

    val welcomer: WelcomeSettings = WelcomeSettings()
) {

    fun starboard() = if(starboardSetup.channel != null) starboardSetup else null

    fun logChannels() = options.featureChannels.values.toList()
        .filter(FeatureChannel::logChannel)
        .map(FeatureChannel::logSettings)

    suspend fun save() {
        GuildConfigurations.mongoConfigurations.updateOne(this, upsert)
    }

    suspend fun getOrCreateFeatures(channel: Long): FeatureChannel = options.featureChannels.getOrPut(channel) {
        FeatureChannel(channel).also { save() }
    }

    suspend fun removeSelf() {
        GuildConfigurations.guildConfigurations.remove(GuildTarget(guildClientId, guildid))
        GuildConfigurations.mongoConfigurations.deleteOneById(this._id)
    }
}

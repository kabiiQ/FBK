package moe.kabii.data.mongodb

import kotlinx.coroutines.runBlocking
import moe.kabii.LOG
import moe.kabii.data.mongodb.guilds.*
import moe.kabii.data.relational.twitter.TwitterTarget
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

    suspend fun findFeatures(target: TwitterTarget): FeatureChannel? {
        val guildId = target.discordChannel.guild?.guildID ?: return null
        val guildConfig = getOrCreateGuild(target.discordClient, guildId)
        return guildConfig.getOrCreateFeatures(target.discordChannel.channelID)
    }
}

// per guild - guildconfiguration collection
data class GuildConfiguration(
    val _id: Id<GuildConfiguration> = newId(),
    val guildid: Long,
    var prefix: String = defaultPrefix,
    val options: OptionalFeatures = OptionalFeatures(),
    val guildCustomCommands: CustomCommands = CustomCommands(),
    val autoRoles: AutoRoles = AutoRoles(),
    val selfRoles: SelfRoles = SelfRoles(),
    val guildSettings: GuildSettings = GuildSettings(),
    val tempVoiceChannels: TempChannels = TempChannels(),
    val commandFilter: CommandFilter = CommandFilter(),
    val musicBot: MusicSettings = MusicSettings(),
    val translator: TranslatorSettings = TranslatorSettings(),
    val guildClientId: Int = 1, // TODO default=1 can be removed after migration

    var starboardSetup: StarboardSetup = StarboardSetup(),
    var starboard: StarboardSetup? = null, // TODO remove after migration

    val welcomer: WelcomeSettings = WelcomeSettings()
) {
    companion object {
        const val defaultPrefix = ";"
    }

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

package moe.kabii.data.mongodb.guilds

import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.util.DiscordEmoji

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
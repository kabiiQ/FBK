package moe.kabii.data.mongodb.guilds

import moe.kabii.data.mongodb.MessageInfo
import moe.kabii.util.DiscordEmoji

data class AutoRoles(
    val joinConfigurations: MutableList<JoinConfiguration> = mutableListOf(),
    val voiceConfigurations: MutableList<VoiceConfiguration> = mutableListOf(),
    val rejoinRoles: MutableMap<Long, LongArray> = mutableMapOf(),
    val reactionConfigurations: MutableList<ReactionRoleConfig> = mutableListOf(),
    val exclusiveRoleSets: MutableList<ExclusiveRoleSet> = mutableListOf(),
    val buttonConfigurations: MutableList<ButtonConfiguration> = mutableListOf()
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

// TODO remove after migrations complete
data class SelfRoles(
    val reactionRoles: MutableList<ReactionRoleConfig> = mutableListOf()
)

data class ReactionRoleConfig(
    val message: MessageInfo,
    val reaction: DiscordEmoji,
    val role: Long
)

data class ButtonConfiguration(
    val message: MessageInfo,
    var type: Int,
    var content: String?,
    val roles: MutableList<ButtonRoles>,
    var max: Int = 0,
    var listRoles: Boolean = true
    ) {
    companion object {
        enum class Type {
            BUTTONS,
            DROPDOWN
        }
    }

    fun isList() = type == 1
}

data class ButtonRoles(
    val role: Long,
    var info: String?,
    var emoji: DiscordEmoji?,
    var alternateName: String?
)
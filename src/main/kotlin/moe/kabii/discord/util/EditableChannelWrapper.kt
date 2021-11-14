package moe.kabii.discord.util

import discord4j.core.spec.legacy.LegacyNewsChannelEditSpec
import discord4j.core.spec.legacy.LegacyTextChannelEditSpec

data class EditableChannelWrapper(
    var name: String? = null
) {
    fun applyTo(spec: LegacyTextChannelEditSpec): LegacyTextChannelEditSpec {
        if(name != null) spec.setName(name)
        return spec
    }

    fun applyTo(spec: LegacyNewsChannelEditSpec): LegacyNewsChannelEditSpec {
        if(name != null) spec.setName(name)
        return spec
    }
}
package moe.kabii.discord.util

import discord4j.core.spec.NewsChannelEditSpec
import discord4j.core.spec.TextChannelEditSpec

data class EditableChannelWrapper(
    var name: String? = null
) {
    fun applyTo(spec: TextChannelEditSpec): TextChannelEditSpec {
        if(name != null) spec.setName(name)
        return spec
    }

    fun applyTo(spec: NewsChannelEditSpec): NewsChannelEditSpec {
        if(name != null) spec.setName(name)
        return spec
    }
}
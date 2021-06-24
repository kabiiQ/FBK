package moe.kabii.util.extensions

import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageCreateSpec

typealias EmbedBlock = EmbedCreateSpec.() -> Unit
typealias EmbedSuspension = suspend EmbedCreateSpec.() -> Unit

class EmbedMessageCreateSpec(val spec: MessageCreateSpec, val embed: EmbedCreateSpec)
typealias MessageEmbedSuspension = suspend EmbedMessageCreateSpec.() -> Unit

typealias UserID = Long
typealias GuildID = Long

annotation class WithinExposedContext
package moe.kabii.util.extensions

import discord4j.core.spec.legacy.LegacyEmbedCreateSpec

typealias EmbedBlock = LegacyEmbedCreateSpec.() -> Unit
typealias EmbedSuspension = suspend LegacyEmbedCreateSpec.() -> Unit

typealias UserID = Long
typealias GuildID = Long

annotation class WithinExposedContext
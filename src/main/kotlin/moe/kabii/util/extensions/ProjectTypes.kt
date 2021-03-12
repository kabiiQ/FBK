package moe.kabii.util.extensions

import discord4j.core.spec.EmbedCreateSpec

typealias EmbedBlock = EmbedCreateSpec.() -> Unit
typealias EmbedReceiver = suspend EmbedCreateSpec.() -> Unit

typealias UserID = Long
typealias GuildID = Long

annotation class WithinExposedContext
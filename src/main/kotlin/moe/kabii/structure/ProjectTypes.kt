package moe.kabii.structure

import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageCreateSpec

typealias EmbedBlock = EmbedCreateSpec.() -> Unit
typealias EmbedReceiver = suspend EmbedCreateSpec.() -> Unit
typealias MessageReceiver = MessageCreateSpec.() -> Unit

typealias UserID = Long
typealias GuildID = Long
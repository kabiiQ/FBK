package moe.kabii.structure

import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.RoleCreateSpec

typealias EmbedReceiver = EmbedCreateSpec.() -> Unit
typealias RoleReceiver = RoleCreateSpec.() -> Unit

typealias UserID = Long
typealias GuildID = Long
typealias TwitchID = Long
typealias RoleID = Long
typealias ChannelID = Long

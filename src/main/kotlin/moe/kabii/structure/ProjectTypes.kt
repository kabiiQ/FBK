package moe.kabii.structure

import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.RoleCreateSpec
import moe.kabii.helix.HelixAPIErr
import moe.kabii.helix.TwitchStream
import moe.kabii.helix.TwitchUser
import moe.kabii.rusty.Result

typealias EmbedReceiver = EmbedCreateSpec.() -> Unit
typealias RoleReceiver = RoleCreateSpec.() -> Unit

typealias UserID = Long
typealias GuildID = Long
typealias TwitchID = Long
typealias RoleID = Long
typealias ChannelID = Long

typealias HelixStream = Result<TwitchStream, HelixAPIErr>
typealias HelixUser = Result<TwitchUser, HelixAPIErr>

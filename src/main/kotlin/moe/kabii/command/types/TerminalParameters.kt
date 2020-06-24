package moe.kabii.command.types

import discord4j.core.GatewayDiscordClient

data class TerminalParameters(
    val discord: GatewayDiscordClient,
    val noCmd: String,
    val args: List<String>
)
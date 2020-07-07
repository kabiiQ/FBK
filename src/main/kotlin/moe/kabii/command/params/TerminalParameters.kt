package moe.kabii.command.params

import discord4j.core.GatewayDiscordClient

data class TerminalParameters(
    val discord: GatewayDiscordClient,
    val noCmd: String,
    val args: List<String>
)
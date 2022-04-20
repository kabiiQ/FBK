package moe.kabii.command.params

import moe.kabii.DiscordInstances

data class TerminalParameters(
    val instances: DiscordInstances,
    val noCmd: String,
    val args: List<String>
)
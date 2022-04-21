package moe.kabii.command.params

import moe.kabii.instances.DiscordInstances

data class TerminalParameters(
    val instances: DiscordInstances,
    val noCmd: String,
    val args: List<String>
)
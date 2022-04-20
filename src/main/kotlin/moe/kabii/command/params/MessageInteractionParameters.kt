package moe.kabii.command.params

import discord4j.core.event.domain.interaction.MessageInteractionEvent
import moe.kabii.FBK

data class MessageInteractionParameters(
    val client: FBK,
    val event: MessageInteractionEvent
)
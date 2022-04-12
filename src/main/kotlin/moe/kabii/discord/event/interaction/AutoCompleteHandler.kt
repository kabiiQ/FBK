package moe.kabii.discord.event.interaction

import discord4j.core.`object`.command.ApplicationCommandInteractionOptionValue
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData
import kotlinx.coroutines.launch
import moe.kabii.LOG
import moe.kabii.command.CommandManager
import moe.kabii.discord.event.EventListener
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.stackTraceString

class AutoCompleteHandler(val manager: CommandManager): EventListener<ChatInputAutoCompleteEvent>(ChatInputAutoCompleteEvent::class) {

    data class Request(
        val event: ChatInputAutoCompleteEvent,
        val guildId: Long?
    ) {
        val value: String = event.focusedOption.value
            .map(ApplicationCommandInteractionOptionValue::asString)
            .orElse("")

        suspend fun respond(choices: Iterable<ApplicationCommandOptionChoiceData>) = event.respondWithSuggestions(
            choices.take(25)
        ).awaitAction()
    }

    override suspend fun handle(event: ChatInputAutoCompleteEvent) {

        val command = manager.commandsDiscord[event.commandName]
        if(command?.autoComplete == null) error("AutoComplete specified, handler missing: ${event.commandName}")

        manager.context.launch {
            val guildId = event.interaction.guildId.orNull()?.asLong()
            try {
                command.autoComplete!!(Request(event, guildId))
            } catch(e: Exception) {
                LOG.error("Uncaught exception in chat autocomplete event: ${command.name}: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }
}
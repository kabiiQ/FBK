package moe.kabii.discord.event.interaction

import discord4j.core.`object`.command.ApplicationCommandInteractionOptionValue
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData
import kotlinx.coroutines.launch
import moe.kabii.instances.DiscordInstances
import moe.kabii.instances.FBK
import moe.kabii.LOG
import moe.kabii.command.CommandManager
import moe.kabii.discord.event.EventListener
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.stackTraceString
import org.apache.commons.lang3.StringUtils

class AutoCompleteHandler(val instances: DiscordInstances): EventListener<ChatInputAutoCompleteEvent>(ChatInputAutoCompleteEvent::class) {

    data class Request(
        val client: FBK,
        val manager: CommandManager,
        val event: ChatInputAutoCompleteEvent,
        val guildId: Long?
    ) {
        val value: String by lazy {
            event.focusedOption.value
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("")
        }

        fun <R> typedValue(transform: (ApplicationCommandInteractionOptionValue) -> R) = event
            .focusedOption.value.map(transform).orNull()

        suspend fun suggest(choices: Iterable<ApplicationCommandOptionChoiceData>) = event.respondWithSuggestions(
            choices
                .map { choice ->
                    val nameLen = choice.name().length > 100
                    val valueLen = choice.value().toString().length > 100
                    if(nameLen || valueLen) {
                        ApplicationCommandOptionChoiceData.builder()
                            .name(if(nameLen) StringUtils.abbreviate(choice.name(), 100) else choice.name())
                            .value(if(valueLen) StringUtils.abbreviate(choice.value().toString(), 100) else choice.value())
                            .build()
                    } else choice
                }
                .take(25)
        ).awaitAction()
    }

    override suspend fun handle(event: ChatInputAutoCompleteEvent) {

        val manager = instances.manager
        val command = manager.commandsDiscord[event.commandName]
        if(command?.autoComplete == null) error("AutoComplete specified, handler missing: ${event.commandName}")

        manager.context.launch {
            val guildId = event.interaction.guildId.orNull()?.asLong()
            val client = instances[event.client]
            try {
                command.autoComplete!!(Request(client, manager, event, guildId))
            } catch(e: Exception) {
                LOG.error("Uncaught exception in chat autocomplete event: ${command.name}: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }
}
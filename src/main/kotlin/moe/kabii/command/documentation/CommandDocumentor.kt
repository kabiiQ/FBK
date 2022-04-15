package moe.kabii.command.documentation

import discord4j.core.`object`.command.ApplicationCommandOption
import discord4j.discordjson.json.ApplicationCommandOptionData
import discord4j.discordjson.json.ApplicationCommandRequest
import moe.kabii.command.Command
import moe.kabii.command.CommandManager
import moe.kabii.util.extensions.capitilized
import moe.kabii.util.extensions.orNull
import java.io.File

object CommandDocumentor {

    private val dir = File("files/commands/doc")

    fun writeCommandDoc(manager: CommandManager, requests: List<ApplicationCommandRequest>) = requests
        .mapNotNull { request ->
            val command = manager.commandsDiscord[request.name()] ?: return@mapNotNull null
            commandToMarkdown(command, request)
        }
        .joinToString("\n")
        .run(File(dir, "header.md").readText()::plus)
        .apply(File(dir, "Command List.md")::writeText)
        .apply(File("Command List.md")::writeText)

    private fun commandToMarkdown(command: Command, request: ApplicationCommandRequest): String {

        val out = StringBuilder()

        // Command name
        out.append("### - ")
        when {
            command.executeChat != null -> out
                .append("`/")
                .append(command.name)
                .append("`:")
            command.executeUser != null -> out
                .append("User Command: `")
                .append(command.name)
                .append("`")
            command.executeMessage != null -> out
                .append("Message Command: `")
                .append(command.name)
                .append("`")
            else -> return ""
        }
        out.append("\n\n")

        // Description
        request.description().toOptional().ifPresent { description ->
            out.append("- ")
                .append(description)
                .append('\n')
        }

        // Wiki link
        if(command.wikiPath != null) {
            out.append("- Wiki: [[")
                .append(command.wikiPath)
                .append("]]\n")
        }
        out.append('\n')

        // returns whether the 'option' table has been created - don't create multiple for one command
        // + builds a subcommand tree
        // if it has regular options, it will not be a subcommand, and vice-versa.
        // however, a command can have multiple regular options which can be grouped together
        data class CommandParseContext(val path: String, val table: Boolean)
        fun optionToMarkdown(ctx: CommandParseContext, option: ApplicationCommandOptionData): CommandParseContext {

            return when(option.type()) {
                ApplicationCommandOption.Type.SUB_COMMAND.value, ApplicationCommandOption.Type.SUB_COMMAND_GROUP.value -> {

                    val sub = "${ctx.path} ${option.name()}"
                    out.append("#### -- `")
                        .append(sub)
                        .append("`\n\n- ")
                        .append(option.description())
                        .append("\n\n")

                    // pass path to any subcommands - folding to pass 'table' state through each option
                    option.options().toOptional().orNull()?.fold(CommandParseContext(sub, false), ::optionToMarkdown)
                    ctx.copy(table = false) // we will never be in a table as a subcommand root
                }
                else -> {
                    // Build option table for any regular options
                    if(!ctx.table) out.append("| Option | Type | Description\n| ---    | ---  | ---\n")

                    out.append("| `")
                        .append(option.name())
                    if(option.required().toOptional().orNull() == true) out.append("*") // * to represent required option
                    out.append("` | ")

                    // Option type
                    val typeName = when(option.type()) {
                        ApplicationCommandOption.Type.BOOLEAN.value -> "True/False"
                        ApplicationCommandOption.Type.NUMBER.value -> "Decimal"
                        else -> ApplicationCommandOption.Type.values().first { type -> type.value == option.type() }.name.lowercase().capitilized()
                    }
                    out.append(typeName)
                        .append(" | ")
                        .append(option.description())
                        .append('\n')

                    ctx.copy(table = true) // once we exit a non-subcommand, we will always have built a table
                }
            }
        }

        request.options().toOptional().orNull()?.fold(CommandParseContext("/${command.name}", false), ::optionToMarkdown)
        return out
            .append('\n')
            .toString()
    }
}
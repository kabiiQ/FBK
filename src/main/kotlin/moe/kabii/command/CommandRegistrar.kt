package moe.kabii.command

import discord4j.common.JacksonResources
import discord4j.core.`object`.command.ApplicationCommandOption
import discord4j.discordjson.json.ApplicationCommandOptionData
import discord4j.discordjson.json.ApplicationCommandRequest
import moe.kabii.command.commands.configuration.setup.base.*
import org.apache.commons.lang3.StringUtils
import java.io.File

object CommandRegistrar {

    fun getAllGlobalCommands(modules: List<ConfigurationModule<*>>): List<ApplicationCommandRequest> = importStaticCommands() + modules.map(::buildConfigCommand)

    /*
    chat, message, and user commands are generated from files of their raw Discord request form
    these all use the same json format
     */
    private fun importStaticCommands(): List<ApplicationCommandRequest> {
        val commands = searchConfigFileTree(File("files/commands/global/"))
        if(commands.isEmpty()) error("Command files not found!") // propagate exceptions to main, will exit

        val mapper = JacksonResources.create().objectMapper
        return commands.map { f -> mapper.readValue(f, ApplicationCommandRequest::class.java) }
    }

    // recursively build configuration list
    private fun searchConfigFileTree(root: File): List<File> {
        val (directory, config) = root.listFiles()!!.partition { f -> f.isDirectory }
        return config.filter { f -> f.extension == "json" } + directory.flatMap(::searchConfigFileTree)
    }

    /*
    "config" commands are global chat commands we generate programmatically and do not use json
     */
    private fun buildConfigCommand(module: ConfigurationModule<*>): ApplicationCommandRequest {

        // generate command arguments from the module's properties
        val base =  ApplicationCommandRequest.builder()
            .name(module.command.name)
            .description("Configurable ${module.name} settings. Run '/${module.command.name} setup' to view all.")
        val builder = module.elements.fold(base) { command, element ->
            // build argument option
            val option = ApplicationCommandOptionData.builder()
                .name(element.propertyFieldName) // "value"
                .description("The new value for ${element.propName}. Leave blank to check current value.")
                .type(element.propertyType)
                .run {

                    // apply bounds if this is numerical type
                    if(element is LongElement) {
                        this
                            .minValue(element.range.first.toDouble())
                            .maxValue(element.range.last.toDouble())
                    } else this
                }
                .run {

                    // apply limits if this is channel type
                    if(element is ChannelElement) {
                        this.channelTypes(element.validTypes.map(ChannelElement.Types::value))
                    } else this
                }
                .required(false)
                .build()

            val resetOption = when(element) {
                is StringElement, is ChannelElement, is AttachmentElement, is CustomElement<*, *> -> {
                    val default = when(element) {
                        is StringElement -> element.default
                        is CustomElement<*, *> -> element.default?.toString()
                        else -> null // channels, attachments -> null
                    } ?: "{empty}"
                    ApplicationCommandOptionData.builder()
                        .name("reset")
                        .description("Reset this option its default value: $default")
                        .type(ApplicationCommandOption.Type.BOOLEAN.value)
                        .required(false)
                        .build()
                }
                else -> null
            }

            // build subcommand for this property
            // /feature <anime> option
            val subCommand = ApplicationCommandOptionData.builder()
                .name(element.propName)
                .description(StringUtils.abbreviate(element.fullName, 100))
                .type(ApplicationCommandOption.Type.SUB_COMMAND.value)
                .addOption(option)
                .run { if(resetOption != null) addOption(resetOption) else this }
                .build()
            command.addOption(subCommand)
        }
        // add a "setup" sub-command - custom configurable embed
        val embedSubCommand = ApplicationCommandOptionData.builder()
            .name("setup")
            .description("View all ${module.name} settings and configure.")
            .type(ApplicationCommandOption.Type.SUB_COMMAND.value)
            .build()

        // add command-specific subcommands
        val custom = module.subCommands

        return builder
            .addOption(embedSubCommand)
            .run {
                // add command-specific subcommands
                if(module.subCommands.isNotEmpty()) addAllOptions(module.subCommands) else this
            }
            .build()
    }
}
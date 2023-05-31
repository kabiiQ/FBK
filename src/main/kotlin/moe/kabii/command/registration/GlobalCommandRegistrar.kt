package moe.kabii.command.registration

import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.command.ApplicationCommandInteractionOptionValue
import discord4j.core.`object`.command.ApplicationCommandOption
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData
import discord4j.discordjson.json.ApplicationCommandOptionData
import discord4j.discordjson.json.ApplicationCommandRequest
import moe.kabii.LOG
import moe.kabii.command.commands.configuration.setup.base.*
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.stackTraceString
import org.apache.commons.lang3.StringUtils
import java.io.File

object GlobalCommandRegistrar : CommandRegistrar {
    private val commandRoot = File("files/commands/")

    fun getAllGlobalCommands(modules: List<ConfigurationModule<*>>): List<ApplicationCommandRequest> = importGlobalCommands() + modules.map(::buildConfigCommand)

    fun getFeatureCommands(feature: String): List<ApplicationCommandRequest> {
        val featureCommands = loadFileCommands(File(commandRoot, "feature/$feature/"))
        return featureCommands.ifEmpty { error("Invalid command feature/subdirectory: $feature") }
    }

    private fun importGlobalCommands(): List<ApplicationCommandRequest> {
        val commands = loadFileCommands(File(commandRoot, "global/"))
        return commands.ifEmpty { error("Command files not found!") }
    }

    /*
    "config" commands are global chat commands we generate programmatically and do not use json
     */
    fun buildConfigCommand(module: ConfigurationModule<*>): ApplicationCommandRequest {

        // generate command arguments from the module's properties
        val base =  ApplicationCommandRequest.builder()
            .name(module.command.name)
            .description("Configurable ${module.name} settings. Run '/${module.command.name} setup' to view all.")
        val builder = module.elements.fold(base) { command, element ->
            // build argument option
            val option = ApplicationCommandOptionData.builder()
                .name("value")
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
                .run {

                    // apply 'choices' for enabled/disabled if this is bool type
                    if(element is BooleanElement) {
                        this
                            .addChoice(
                                ApplicationCommandOptionChoiceData.builder().name("Enabled").value(1).build()
                            )
                            .addChoice(
                                ApplicationCommandOptionChoiceData.builder().name("Disabled").value(0).build()
                            )
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
                .name(StringUtils.abbreviate(element.propName.lowercase(), 32))
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

        val embedAltSubCommand = embedSubCommand.withName("config")

        return builder
            .addOption(embedSubCommand)
            .addOption(embedAltSubCommand)
            .run {
                // add command-specific subcommands
                if(module.subCommands.isNotEmpty()) addAllOptions(module.subCommands) else this
            }
            .build()
    }

    private fun optionToString(opt: ApplicationCommandInteractionOption?): String = if(opt != null) {
        fun <T> option(transform: (ApplicationCommandInteractionOptionValue) -> T) = " - Option ${opt.name} = ${opt.value.orNull()?.run(transform)}"
        try {
            when(opt.type) {
                ApplicationCommandOption.Type.SUB_COMMAND, ApplicationCommandOption.Type.SUB_COMMAND_GROUP -> " - SubCommand ${opt.name} ->" + opt.options.joinToString("", transform = ::optionToString)
                ApplicationCommandOption.Type.STRING -> option(ApplicationCommandInteractionOptionValue::asString)
                ApplicationCommandOption.Type.INTEGER -> option(ApplicationCommandInteractionOptionValue::asLong)
                ApplicationCommandOption.Type.BOOLEAN -> option(ApplicationCommandInteractionOptionValue::asBoolean)
                else -> " - Option ${opt.name} -> ${opt.type.name}}"
            }
        } catch(e: Exception) {
            LOG.debug(e.stackTraceString)
            "PARSE EXCEPTION"
        }
    } else ""

    fun optionsToString(list: List<ApplicationCommandInteractionOption>) = list.joinToString("", transform = ::optionToString)
}
package moe.kabii.command.commands.configuration.setup

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.DiscordParameters
import moe.kabii.command.fbkColor
import moe.kabii.structure.EmbedBlock
import kotlin.reflect.KMutableProperty1

sealed class ConfigurationElement<T>(val fullName: String, val aliases: List<String>)
class StringElement<T>(
    fullName: String,
    aliases: List<String>,
    val prop: KMutableProperty1<T, String>,
    val prompt: String,
    val default: String
) : ConfigurationElement<T>(fullName, aliases)

class BooleanElement<T>(
    fullName: String,
    aliases: List<String>,
    val prop: KMutableProperty1<T, Boolean>
) : ConfigurationElement<T>(fullName, aliases)

class DoubleElement<T>(
    fullName: String,
    aliases: List<String>,
    val prop: KMutableProperty1<T, Double>,
    val range: ClosedRange<Double>,
    val prompt: String,
    val default: Double
) : ConfigurationElement<T>(fullName, aliases)

class LongElement<T>(
    fullName: String,
    aliases: List<String>,
    val prop: KMutableProperty1<T, Long>,
    val range: LongRange,
    val prompt: String,
    val default: Long
) : ConfigurationElement<T>(fullName, aliases)

open class ConfigurationModule<T>(val name: String, vararg val elements: ConfigurationElement<T>)

class Configurator<T>(private val name: String, private val module: ConfigurationModule<T>, private val instance: T) {
    fun getValue(element: ConfigurationElement<T>) = when(element) {
        is StringElement -> element.prop.get(instance)
        is BooleanElement -> if(element.prop.get(instance)) "enabled" else "disabled"
        is DoubleElement -> element.prop.get(instance).toString()
        is LongElement -> element.prop.get(instance).toString()
    }
    private fun getName(element: ConfigurationElement<*>) = "${element.fullName} **(${element.aliases.first()})**"

    suspend fun run(origin: DiscordParameters): Boolean { // returns if a property was modified and the config should be saved
        fun updatedEmbed(element: ConfigurationElement<T>, new: Any) = origin.embed {
            setTitle("Configuration Updated")
            val property = element.aliases.first()
            val newState = when(element) {
                is BooleanElement -> if(new as Boolean) "**enabled**" else "**disabled**"
                else -> "set to **$new**"
            }
            setDescription("The option **$property** has been $newState.")
        }

        // <command> (no args) -> full menu embed

        if(origin.args.isEmpty()) {
            val configEmbed: EmbedBlock = {
                fbkColor(this)
                setAuthor(name, null, null)
                // not filtering or optimizing to preserve the natural indexes here - could use manually assigned indexes otherwise
                if(module.elements.any { element -> element is BooleanElement }) {
                    setTitle("Select the feature to be toggled/edited using its ID.")
                    // feature toggles - these can be made into FeatureElements if we have other toggles later on
                    val enabled = module.elements.mapIndexedNotNull { id, element ->
                        if(element is BooleanElement && element.prop.get(instance)) "${id+1}. ${getName(element)}" else null
                    }.joinToString("\n").ifEmpty { "No ${module.name} features are enabled." }
                    val available = module.elements.mapIndexedNotNull { id, element ->
                        if(element is BooleanElement && !element.prop.get(instance)) "${id+1}. ${getName(element)}" else null
                    }.joinToString("\n").ifEmpty { "All ${module.name} features are enabled." }
                    addField("Enabled Features", enabled, true)
                    addField("Available (Disabled) Features", available, true)
                }
                module.elements.mapIndexedNotNull { id, element ->
                    if(element !is BooleanElement) "${id+1}. ${getName(element)}:\n${getValue(element)}" else null
                }.run {
                    if(isNotEmpty()) addField("Custom Settings:", joinToString("\n"), false)
                }
                setFooter("\"exit\" to save and exit immediately.", null)
            }

            val menu = origin.embedBlock(configEmbed).awaitSingle()
            while(true) {
                val inputStr = origin.getString(timeout = 120000L) ?: break
                val input = inputStr.toIntOrNull() ?: continue
                if(input !in 1..module.elements.size) break
                when(val element = module.elements[input-1]) {
                    is BooleanElement -> element.prop.set(instance, !element.prop.get(instance)) // toggle property
                    is StringElement -> {
                        // prompt user for new value
                        val prompt = origin.embed(element.prompt).awaitSingle()
                        val response = origin.getString(timeout = null)
                        if(response != null) {
                            if(response.toLowerCase() == "reset") {
                                element.prop.set(instance, element.default)
                            } else {
                                element.prop.set(instance, response)
                            }
                        }
                        prompt.delete().subscribe()
                    }
                    is DoubleElement -> {
                        val prompt = origin.embed(element.prompt).awaitSingle()
                        val response = origin.getDouble(element.range, timeout = 120000L)
                        if(response != null) element.prop.set(instance, response)
                        prompt.delete().subscribe()
                    }
                    is LongElement -> {
                        val prompt = origin.embed(element.prompt).awaitSingle()
                        val response = origin.getLong(element.range, timeout = 120000L)
                        if(response != null) element.prop.set(instance, response)
                        prompt.delete().subscribe()
                    }
                }
                menu.edit { message ->
                    message.setEmbed(configEmbed)
                }.awaitSingle()
            }
            menu.delete().subscribe()
            return true
        }

        val targetElement = origin.args[0].toLowerCase()

        // <command> list/all -> list current config

        if(targetElement == "list") {
            origin.embed {
                setTitle("Current ${module.name} configuration:")
                module.elements.forEach { element ->
                    addField(getName(element), getValue(element), true)
                }
            }.awaitSingle()
            return false
        }

        val element = module.elements.find { prop -> prop.aliases.any { alias -> alias.toLowerCase() == targetElement.toLowerCase() } }
        if(element == null) {
            origin.error("Invalid setting **$targetElement**. The available settings can be found with **${origin.alias} list**. You can also run **${origin.alias}** without any arguments to change settings using an interactive embed.").awaitSingle()
            return false
        }
        val tag = element.aliases.first()
        // <command> prop -> manual get
        if(origin.args.size == 1) {
            origin.embed {
                setTitle("From ${module.name} configuration:")
                addField(getName(element), getValue(element), false)
            }.awaitSingle()
            return false
        }

        // <command> prop <toggle/reset> -> specific actions
        if(origin.args.size == 2) {
            when(origin.args[1].toLowerCase()) {
                "toggle" -> {
                    if(element !is BooleanElement) {
                        origin.error("The setting **$tag** is not a toggle.").awaitSingle()
                        return false
                    }
                    val new = !element.prop.get(instance)
                    element.prop.set(instance, new)
                    updatedEmbed(element, new).subscribe()
                    return true
                }
                "reset" -> {
                    if(element !is StringElement)  {
                        origin.error("The setting **$tag** is not a resettable custom string.").awaitSingle()
                        return false
                    }
                    val new = element.default
                    element.prop.set(instance, new)
                    updatedEmbed(element, new).subscribe()
                    return true
                }
            }
        }

        // <command> prop <new value> -> manual set, check input types
        when(element) {
            is BooleanElement -> {
                val input = origin.args[1].toLowerCase()
                val bool = when {
                    input.startsWith("y")
                            || input.startsWith("en")
                            || input.startsWith("t")
                            || input == "1"
                            || input == "on"
                    -> true
                    input.startsWith("n")
                            || input.startsWith("dis")
                            || input.startsWith("f")
                            || input == "0"
                            || input == "off"
                    -> false
                    else -> null
                }
                if(bool == null) {
                    origin.error("The setting **$tag** is a toggle, I can not set it to **$input**. Example: **${origin.alias} $tag enable**. You can also run **${origin.alias} toggle $tag**").awaitSingle()
                    return false
                }
                element.prop.set(instance, bool)
                updatedEmbed(element, bool).subscribe()
                return true
            }
            is StringElement -> {
                val input = origin.args.drop(1).joinToString(" ")
                element.prop.set(instance, input)
                updatedEmbed(element, input).subscribe()
                return true
            }
            is DoubleElement -> {
                val input = origin.args[1].toDoubleOrNull()
                if(input == null) {
                    origin.error("The setting **$tag** is a decimal value, I can not set it to **$input**. Example: **${origin.alias} $tag .5**").awaitSingle()
                    return false
                }
                element.prop.set(instance, input)
                updatedEmbed(element, input).subscribe()
                return true
            }
            is LongElement -> {
                val input = origin.args[1].toLongOrNull()
                if(input == null) {
                    origin.error("The setting **$tag** is an integer value, I can not set it to **$input**. Example: **${origin.alias} $tag 4**").awaitSingle()
                    return false
                }
                element.prop.set(instance, input)
                updatedEmbed(element, input).subscribe()
                return true
            }
        }
    }
}
package moe.kabii.command.params

import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.command.ApplicationCommandInteractionOptionValue
import discord4j.core.`object`.command.Interaction
import discord4j.core.`object`.entity.channel.Channel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import moe.kabii.util.extensions.orNull
import java.util.Optional
import kotlin.reflect.KClass

class ChatCommandArguments(val args: (String) -> Optional<ApplicationCommandInteractionOption>) {

    constructor(event: ChatInputInteractionEvent) : this(event::getOption)
    constructor(subcommand: ApplicationCommandInteractionOption) : this(subcommand::getOption)

    private fun <T> get(name: String, transform: (ApplicationCommandInteractionOptionValue) -> T) = args(name)
        .flatMap(ApplicationCommandInteractionOption::getValue)
        .map(transform)

    private fun _string(name: String) = get(name, ApplicationCommandInteractionOptionValue::asString)
    fun string(name: String) = _string(name).get()
    fun optStr(name: String) = _string(name).orNull()

    fun _int(name: String) = get(name, ApplicationCommandInteractionOptionValue::asLong)
    fun int(name: String) = _int(name).get()
    fun optInt(name: String) = _int(name).orNull()

    fun _bool(name: String) = get(name, ApplicationCommandInteractionOptionValue::asBoolean)
    fun bool(name: String) = _bool(name).get()
    fun optBool(name: String) = _bool(name).orNull()

    fun _user(name: String) = get(name, ApplicationCommandInteractionOptionValue::asUser)
    fun user(name: String) = _user(name).get()
    fun optUser(name: String) = _user(name).orNull()

    fun _double(name: String) = get(name, ApplicationCommandInteractionOptionValue::asDouble)
    fun double(name: String) = _double(name).get()
    fun optDouble(name: String) = _double(name).orNull()

    fun _role(name: String) = get(name, ApplicationCommandInteractionOptionValue::asRole)
    fun role(name: String) = _role(name).get()
    fun optRole(name: String) = _role(name).orNull()

    fun _baseChannel(name: String) = get(name, ApplicationCommandInteractionOptionValue::asChannel)
    fun baseChannel(name: String) = _baseChannel(name).get()
    fun optBaseChannel(name: String) = _baseChannel(name).orNull()

    fun <T: Channel> _channel(name: String, type: KClass<T>) = _baseChannel(name).map { chan -> chan.ofType(type.java) }
    fun <T: Channel> channel(name: String, type: KClass<T>) = _channel(name, type).get()
    fun <T: Channel> optChannel(name: String, type: KClass<T>) = _channel(name, type).orNull()
}
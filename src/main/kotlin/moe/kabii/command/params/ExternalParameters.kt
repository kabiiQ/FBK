package moe.kabii.command.params

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.TextChannel
import moe.kabii.instances.DiscordInstances
import moe.kabii.instances.FBK
import moe.kabii.net.api.commands.ExternalCommand
import moe.kabii.util.i18n.Translations

data class ExternalParameters(
    val instances: DiscordInstances,
    val command: ExternalCommand,
    val fbk: FBK,
    val user: User,
    val channel: TextChannel
) {
    fun i18n(identifier: String): String = selecti18nMethod(identifier)

    fun i18n(identifier: String, vararg variables: Pair<String, Any>): String = selecti18nMethod(identifier, namedVars = variables)

    fun i18n(identifier: String, vararg variables: Any): String = selecti18nMethod(identifier, orderedVars = variables)

    private fun selecti18nMethod(stringIdentifier: String, namedVars: Array<out Pair<String, Any>>? = null, orderedVars: Array<out Any>? = null): String {
        val locale = Translations.locales[Translations.defaultLocale]!!
        return if(namedVars != null)
            locale.responseString(stringIdentifier, *namedVars)
        else
            locale.responseString(stringIdentifier, *(orderedVars.orEmpty()))
    }
}
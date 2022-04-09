package moe.kabii.data.mongodb.guilds

import moe.kabii.command.Command

// using custom solution rather than something like an enum as this is a database class
data class CommandFilter(
    val blacklist: MutableSet<String> = mutableSetOf(),
    val whitelist: MutableSet<String> = mutableSetOf()
) {
    var whitelisted: Boolean = false
    private set
    var blacklisted: Boolean = true
    private set

    fun useBlacklist() {
        whitelisted = false
        blacklisted = true
    }

    fun useWhitelist() {
        blacklisted = false
        whitelisted = true
    }

    fun isCommandEnabled(command: Command): Boolean {
        // check all aliases against the list in case things got shuffled
        return when {
            // some commands can not be disabled in order to avoid being locked out of the bot
            command.commandExempt -> true
            // normal behavior, some commands might be disabled
            blacklisted -> !blacklist.contains(command.name)
            // optional behavior, some commands might be enabled
            whitelisted -> whitelist.contains(command.name)
            else -> error("Illegal blacklist/whitelist flag configuration")
        }
    }
}
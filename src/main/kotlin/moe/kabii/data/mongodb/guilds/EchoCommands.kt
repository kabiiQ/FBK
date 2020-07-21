package moe.kabii.data.mongodb.guilds

data class EchoCommands(
    val commands: MutableList<EchoCommand> = mutableListOf()) {

    fun removeByName(command: String) = commands.removeIf { it.command == command }

    fun insertIsUpdated(command: EchoCommand): Boolean {
        val replacing = commands.removeIf { it.command == command.command }
        commands.add(command)
        return replacing
    }
}

data class EchoCommand(
        val command: String,
        var response: String,
        var restrict: Boolean)
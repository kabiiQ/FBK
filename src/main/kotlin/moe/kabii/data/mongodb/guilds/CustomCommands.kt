package moe.kabii.data.mongodb.guilds

data class CustomCommands(
    val commands: MutableList<CustomCommand> = mutableListOf()) {

    fun removeByName(command: String) = commands.removeIf { it.command == command }

    fun insertIsUpdated(command: CustomCommand): Boolean {
        val replacing = commands.removeIf { it.command == command.command }
        commands.add(command)
        return replacing
    }
}

data class CustomCommand(
        val command: String,
        var response: String,
        var restrict: Boolean)
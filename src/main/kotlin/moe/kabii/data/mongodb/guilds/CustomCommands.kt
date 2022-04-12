package moe.kabii.data.mongodb.guilds

data class CustomCommands(
    val commands: MutableList<CustomCommand> = mutableListOf()) {

    fun removeByName(commandName: String) = commands.removeIf { c -> c.name == commandName }

    fun insertIsUpdated(command: CustomCommand): Boolean {
        val replacing = commands.removeIf(command::equals)
        commands.add(command)
        return replacing
    }
}

class CustomCommand(
        val name: String,
        val description: String,
        var response: String,
        var ephemeral: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(javaClass != other?.javaClass) return false
        other as CustomCommand
        if(name != other.name) return false
        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
package moe.kabii.util.constants

/***
 * Discord Opcode constants
 */
object Opcode {
    const val UNKNOWN_CHANNEL = 10003
    const val UNKNOWN_GUILD = 10004
    const val UNKNOWN_ROLE = 10011
    const val UNKNOWN_EVENT = 10070
    const val PIN_LIMIT = 30003
    const val MAXIMUM_EVENTS = 30038
    const val MISSING_ACCESS = 50001
    const val PERMISSIONS = 50013
    const val FINISHED_EVENT = 180000

    fun notFound(opcode: Int) = opcode == UNKNOWN_CHANNEL || opcode == UNKNOWN_GUILD || opcode == UNKNOWN_ROLE
    fun denied(opcode: Int) = opcode == MISSING_ACCESS || opcode == PERMISSIONS
}
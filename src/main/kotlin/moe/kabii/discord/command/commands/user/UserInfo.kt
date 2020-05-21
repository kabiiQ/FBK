package moe.kabii.discord.command.commands.user

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.command.Command
import moe.kabii.discord.util.Search
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object UserInfo : Command("user", "whoami", "jointime") {
    private val formatter = DateTimeFormatter.ofPattern("MMMM dd yyyy @ HH:mm:ss 'UTC'")

    init {
        discord {
            val targetUser = if(args.isNotEmpty()) {
                Search.user(this@discord, noCmd, guild) ?: author

            } else author

            embed {
                setAuthor("${targetUser.username}#${targetUser.discriminator}", null, targetUser.avatarUrl)
                val accountCreation = targetUser.id.timestamp.atZone(ZoneOffset.UTC).toLocalDateTime()
                addField("Account created", formatter.format(accountCreation), false)

                if(guild != null) {
                    val guildJoin = targetUser.asMember(guild.id).block().joinTime.atZone(ZoneOffset.UTC).toLocalDateTime()
                    addField("Joined ${guild.name}", formatter.format(guildJoin), false)
                }
            }.awaitSingle()
        }
    }
}
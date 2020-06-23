package moe.kabii.command.commands.user

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Search
import moe.kabii.structure.tryAwait
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object UserInfo : Command("user", "whoami", "jointime") {
    private val formatter = DateTimeFormatter.ofPattern("MMMM dd yyyy @ HH:mm:ss 'UTC'")

    init {
        discord {
            val targetUser = if(args.isNotEmpty()) {
                val searchResult = Search.user(this@discord, noCmd, guild)
                if(searchResult != null) searchResult else {
                    error("Unable to find user **$noCmd**.").awaitSingle()
                    return@discord
                }
            } else author

            val guildMember = guild?.run { targetUser.asMember(guild.id) }?.tryAwait()?.orNull()
            embed {
                setAuthor("${targetUser.username}#${targetUser.discriminator}", null, targetUser.avatarUrl)
                val accountCreation = targetUser.id.timestamp.atZone(ZoneOffset.UTC).toLocalDateTime()
                addField("Account created", formatter.format(accountCreation), false)

                if(guildMember != null) {
                    val guildJoin = guildMember.joinTime.atZone(ZoneOffset.UTC).toLocalDateTime()
                    addField("Joined ${guild!!.name}", formatter.format(guildJoin), false)
                }
            }.awaitSingle()
        }
    }
}
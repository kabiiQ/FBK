package moe.kabii.command.commands.user

import discord4j.common.util.TimestampFormat
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.tryAwait
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object UserInfo : Command("user", "whoami", "jointime", "whois", "who") {
    private val formatter = DateTimeFormatter.ofPattern("MMMM dd yyyy @ HH:mm:ss 'UTC'")

    override val wikiPath = "Discord-Info-Commands#user-info-summary-server-join-time"

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
            embed(targetUser) {
                addField("Account created", TimestampFormat.LONG_DATE_TIME.format(targetUser.id.timestamp), false)

                val joinTime = guildMember?.joinTime?.orNull()
                if(joinTime != null) {
                    addField("Joined ${guild!!.name}", TimestampFormat.LONG_DATE_TIME.format(joinTime), false)
                }
            }.awaitSingle()
        }
    }
}
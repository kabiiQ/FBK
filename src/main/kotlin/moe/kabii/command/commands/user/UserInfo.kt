package moe.kabii.command.commands.user

import discord4j.common.util.TimestampFormat
import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.tryAwait
import java.time.format.DateTimeFormatter

object UserInfo : Command("user", "whoami", "jointime", "whois", "who") {
    private val formatter = DateTimeFormatter.ofPattern("MMMM dd yyyy @ HH:mm:ss 'UTC'")

    override val wikiPath = "Discord-Info-Commands#user-info-summary-server-join-time"

    init {
        discord {
            val targetUser = if(args.isNotEmpty()) {
                val searchResult = Search.user(this@discord, noCmd, guild)
                if(searchResult != null) searchResult else {
                    send(Embeds.error("Unable to find user **$noCmd**.")).awaitSingle()
                    return@discord
                }
            } else author

            val guildMember = guild?.run { targetUser.asMember(guild.id) }?.tryAwait()?.orNull()
            val joinTime = guildMember?.joinTime?.orNull()?.run {
                EmbedCreateFields.Field.of("Joined ${guild!!.name}", TimestampFormat.LONG_DATE_TIME.format(this), false)
            }
            send(
                Embeds.fbk(targetUser)
                    .withFields(mutableListOf(
                        EmbedCreateFields.Field.of("Account created", TimestampFormat.LONG_DATE_TIME.format(targetUser.id.timestamp), false),
                        joinTime
                    ).filterNotNull())
            ).awaitSingle()
        }
    }
}
package moe.kabii.command.commands.ps2

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.ps2.polling.PS2Parser
import moe.kabii.util.DurationFormatter
import java.time.Duration
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object PS2PlayerLookup : Command("ps2who") {
    override val wikiPath: String? = null
    override val skipRegistration = true

    init {
        discord {
            guildFeatureVerify(GuildSettings::ps2Commands, "PS2")
            val userArg = args.string("username")
            val user = try {
                PS2Parser.searchPlayerByName(userArg)
            } catch(e: Exception) {
                ereply(Embeds.error("Unable to reach PS2 API.")).awaitSingle()
                return@discord
            }
            if(user == null) {
                ireply(Embeds.error("Unable to find PS2 user **'$userArg'**.")).awaitSingle()
                return@discord
            }
            val outfit = if(user.outfit != null) "[${user.outfit.tag}] " else ""
            val asp = if(user.prestige) "ASP" else "BR"
            val playersSite = "https://www.planetside2.com/players/#!/${user.characterId}"
            val playDuration = Duration.ofMinutes(user.times.minutesPlayed.toLong())
            val creationTime = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.from(ZoneOffset.UTC))
                .format(user.times.creation)

            ireply(
                Embeds.other(user.faction.color)
                    .withAuthor(EmbedCreateFields.Author.of("$outfit${user.name.first} - $asp${user.battleRank}", playersSite, user.faction.image))
                    .withTitle("Server: ${user.world?.name ?: "Unknown"}")
                    .withFields(mutableListOf(
                        EmbedCreateFields.Field.of("Playtime", DurationFormatter(playDuration).inputTime, true),
                        EmbedCreateFields.Field.of("Certs Available/Earned", "${user.certs.availableCerts}/${user.certs.totalCerts}", true),
                        EmbedCreateFields.Field.of("Daily Ribbons Earned", user.dailyRibbon.toString(), false),
                        EmbedCreateFields.Field.of("Character created", creationTime, true)
                    ))
                    .run {
                        if(user.online) withFooter(EmbedCreateFields.Footer.of("ONLINE", null))
                        else withFooter(EmbedCreateFields.Footer.of("Offline, last online ", null)).withTimestamp(user.times.lastSave)
                    }
            ).awaitSingle()
        }
    }
}
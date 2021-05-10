package moe.kabii.command.commands.ps2

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.discord.trackers.ps2.polling.PS2Parser
import moe.kabii.util.DurationFormatter
import java.time.Duration
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object PS2PlayerLookup : Command("ps2who", "ps2player", "ps2whois", "pswhois", "psplayer", "pswho") {
    override val wikiPath: String? = null

    init {
        discord {
            featureVerify(GuildSettings::ps2Commands, "PS2")
            if(args.isEmpty()) {
                usage("**ps2who** is used to look up a player by name.", "ps2who <username>").awaitSingle()
                return@discord
            }
            val user = try {
                PS2Parser.searchPlayerByName(args[0])
            } catch(e: Exception) {
                error("Unable to reach PS2 API.").awaitSingle()
                return@discord
            }
            if(user == null) {
                error("Unable to find PS2 user **'${args[0]}'**.").awaitSingle()
                return@discord
            }
            chan.createEmbed { spec ->
                val outfit = if(user.outfit != null) "[${user.outfit.tag}] " else ""
                spec.setColor(user.faction.color)
                val asp = if(user.prestige) "ASP" else "BR"

                val playersSite = "https://www.planetside2.com/players/#!/${user.characterId}"
                spec.setAuthor("$outfit${user.name.first} - $asp${user.battleRank}", playersSite, user.faction.image)
                val playDuration = Duration.ofMinutes(user.times.minutesPlayed.toLong())
                val creationTime = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.from(ZoneOffset.UTC))
                    .format(user.times.creation)

//                spec.addField("Server", user.world?.name ?: "Unknown", false)
                spec.setTitle("Server: ${user.world?.name ?: "Unknown"}")
                spec.addField("Playtime", DurationFormatter(playDuration).inputTime, true)
                spec.addField("Certs Available/Earned", "${user.certs.availableCerts}/${user.certs.totalCerts}", true)
                spec.addField("Daily Ribbons Earned", user.dailyRibbon.toString(), false)
                spec.addField("Character created", creationTime, true)

                if(user.online) {
                    spec.setFooter("ONLINE", null)
                } else {
                    spec.setFooter("Offline, last online ", null)
                    spec.setTimestamp(user.times.lastSave)
                }
            }.awaitSingle()
        }
    }
}
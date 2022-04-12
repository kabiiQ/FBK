package moe.kabii.command.commands.ps2

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.ps2.polling.FisuPS2Parser
import moe.kabii.trackers.ps2.polling.PS2Parser
import moe.kabii.trackers.ps2.polling.json.PS2FisuPopulation
import moe.kabii.trackers.ps2.store.PS2Faction
import moe.kabii.util.constants.EmojiCharacters

object PS2ServerStatus : Command("ps2servers") {
    override val wikiPath: String? = null

    init {
        chat {
            guildFeatureVerify(GuildSettings::ps2Commands, "PS2")
            // get all servers and list status
            val servers = try {
                PS2Parser.getServers()
            } catch(e: Exception) {
                ereply(Embeds.error("Unable to reach PS2 API.")).awaitSingle()
                return@chat
            }

            val populations = try {
                FisuPS2Parser.requestServerPopulations(servers)
            } catch(e: Exception) {
                null
            }

            val serverList = servers
                .associateWith { server ->
                    // server pop may be unavailable, continue to execute command without pop
                    if(server.state == "online") populations?.get(server)
                    else PS2FisuPopulation(server.worldId) // population api is available but the ps2 server is offline
                }
                .toList()
                .sortedByDescending { (_, pop) -> pop?.total ?: 0 }
                .joinToString("\n\n") { (server, pop) ->
                    val state = if (server.state == "online") {

                        if (pop != null) {
                            val factionPops = PS2Faction.values()
                                .associateWith { faction ->
                                    faction.populationMapping.get(pop)
                                }
                                .toList() // convert to something we can sort
                                .sortedByDescending(Pair<PS2Faction, Int>::second)
                                .joinToString(" + ") { (faction, online) ->
                                    val popPct = if(pop.total > 0) {
                                        (online.toDouble() / pop.total) * 100
                                    } else 0.0
                                    val popStr = if(popPct >= 1 || popPct < 0.1) "%.0f" else "%.1f"
                                    "${popStr.format(popPct)}% ${faction.emoji}"
                                }

                            "${pop.total} online ${EmojiCharacters.checkBox}\n${EmojiCharacters.spacer}$factionPops"
                        } else "? online ${EmojiCharacters.checkBox}" // if pop not available
                    } else "${server.state} ${EmojiCharacters.redX}" // if server is not reported as online
                    "**${server.name}**: $state"
                }

            ireply(
                Embeds.fbk(serverList).withAuthor(EmbedCreateFields.Author.of("PlanetSide 2 Server Status", null, null))
            ).awaitSingle()
        }
    }
}
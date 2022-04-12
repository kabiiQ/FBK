package moe.kabii.command.commands.ps2

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.pagination.PaginationUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.ps2.polling.PS2Parser
import moe.kabii.trackers.ps2.polling.json.PS2Outfit
import moe.kabii.trackers.ps2.polling.json.PS2OutfitMember

object PS2OutfitLookup : Command("ps2outfit") {
    override val wikiPath: String? = null

    init {
        chat {
            val args = subArgs(subCommand)
            when(subCommand.name) {
                "tag" -> {
                    wrapLookup(this, args.string("tag"), PS2Parser::searchOutfitByTag)
                }
                "name" -> {
                    wrapLookup(this, args.string("name"), PS2Parser::searchOutfitByName)
                }
            }
        }
    }

    private suspend fun wrapLookup(origin: DiscordParameters, query: String, search: suspend (String) -> PS2Outfit?) {
        val outfit = try {
            search(query)
        } catch(e: Exception) {
            origin.ereply(Embeds.error("Unable to reach PS2 API.")).awaitSingle()
            return
        }
        if(outfit != null) displayOutfit(origin, outfit)
        else origin.ereply(Embeds.error("Unable to find PS2 outfit **'$query'**.")).awaitSingle()
    }

    private suspend fun displayOutfit(origin: DiscordParameters, outfit: PS2Outfit) {
        val leader = outfit.members.first { member -> member.name == outfit.leader.name }
        val onlineMembers = outfit.members
            .minus(leader)
            .filter(PS2OutfitMember::online)
            .mapNotNull { member -> member.name?.first }

        val status = if(leader.online) "ONLINE" else "offline"
        val count = "${onlineMembers.count()}/${outfit.memberCount}"
        val header = "Outfit leader: ${leader.name?.first} - $status\n\nOnline members ($count):"

        PaginationUtil.paginateListAsDescription(origin, onlineMembers, descHeader = header) {
            this
                .withColor(outfit.leader.faction.color)
                .withAuthor(EmbedCreateFields.Author.of("[${outfit.tag.orEmpty()}] ${outfit.name}", null, outfit.leader.faction.image))
        }
    }
}
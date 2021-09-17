package moe.kabii.command.commands.ps2

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.GuildSettings
import moe.kabii.discord.conversation.PaginationUtil
import moe.kabii.discord.trackers.ps2.polling.PS2Parser
import moe.kabii.discord.trackers.ps2.polling.json.PS2Outfit
import moe.kabii.discord.trackers.ps2.polling.json.PS2OutfitMember
import moe.kabii.discord.util.Embeds

object PS2OutfitLookupCommands : CommandContainer {

    object PS2OutfitLookupByTag : Command("ps2outfit", "ps2outfit:tag", "psoutfit", "psfit", "ps2fit") {
        override val wikiPath: String? = null // todo

        init {
            discord {
                guildFeatureVerify(GuildSettings::ps2Commands, "PS2")
                if(args.isEmpty()) {
                    usage("**ps2outfit** is used to look up and outfit by their tag. **ps2outfit:name** can be used to look up by name.", "ps2outfit <TAG>")
                    return@discord
                }
                wrapLookup(this, args[0], PS2Parser::searchOutfitByTag)
            }
        }
    }

    object PS2OutfitLookupByName : Command("ps2outfit:name", "psoutfit:name", "psfit:name", "ps2fit:name") {
        override val wikiPath: String? = null // todo

        init {
            discord {
                guildFeatureVerify(GuildSettings::ps2Commands, "PS2")
                if(args.isEmpty()) {
                    usage("**ps2outfit:name is used to look up an outfit by their full name, in the event they have no tag.", "ps2outfit:name <outfit name>").awaitSingle()
                    return@discord
                }
                wrapLookup(this, noCmd, PS2Parser::searchOutfitByName)
            }
        }
    }

    private suspend fun wrapLookup(origin: DiscordParameters, query: String, search: suspend (String) -> PS2Outfit?) {
        val outfit = try {
            search(query)
        } catch(e: Exception) {
            origin.reply(Embeds.error("Unable to reach PS2 API.")).awaitSingle()
            return
        }
        if(outfit != null) displayOutfit(origin, outfit)
        else origin.reply(Embeds.error("Unable to find PS2 outfit **'$query'**.")).awaitSingle()
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
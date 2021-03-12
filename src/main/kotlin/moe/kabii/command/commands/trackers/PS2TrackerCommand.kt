package moe.kabii.command.commands.trackers

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.FeatureDisabledException
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.ps2.PS2Tracks
import moe.kabii.discord.trackers.PS2Target
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.discord.util.errorColor
import moe.kabii.discord.util.fbkColor
import moe.kabii.ps2.polling.PS2Parser
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString

object PS2TrackerCommand : TrackerCommand {

    override suspend fun track(origin: DiscordParameters, target: TargetArguments, features: FeatureChannel?) {
        if(origin.guild != null) {
            if(features == null || !features.ps2Channel) throw FeatureDisabledException("ps2", origin)
        }

        val ps2Target = requireNotNull(target.site as? PS2Target) { "Invalid target arguments provided to PS2TrackerCommand" }

        // call api to get respective census id for object
        val input = target.identifier
        val typeName = target.site.alias.firstOrNull()

        val notice = origin.embed("Searching for $typeName **$input**.").awaitSingle()
        val censusId = try {
            getCensusId(ps2Target, input)
        } catch(e: Exception) {
            LOG.warn("Error calling PS2 API: ${e.message}")
            LOG.debug(e.stackTraceString)
            return
        }
        if(censusId == null) {
            notice.edit { spec -> spec.setEmbed { embed ->
                errorColor(embed)
                embed.setDescription("Unable to find PS2 $typeName **$input**.")
            } }
            return
        }

        propagateTransaction {

            // track if not already
            val existing = PS2Tracks.TrackTarget.getExisting(censusId, origin.chan.id.asLong())
            if(existing != null) {
                notice.edit { spec -> spec.setEmbed {embed ->
                    errorColor(embed)
                    embed.setDescription("$typeName/$input is already tracked in this channel.")
                } }.awaitSingle()
                return@propagateTransaction
            }
            PS2Tracks.TrackTarget.new {
                this.censusId = censusId
                this.discordChannel = DiscordObjects.Channel.getOrInsert(origin.chan.id.asLong(), origin.guild?.id?.asLong())
                this.type = ps2Target.dbType
            }
            notice.edit { spec -> spec.setEmbed { embed ->
                fbkColor(embed)
                embed.setDescription("Now tracking $typeName **$input**.")
            } }.awaitSingle()
        }
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetArguments) {
        val ps2Target = requireNotNull(target.site as? PS2Target) { "Invalid target arguments provided to PS2TrackerCommand" }
        val typeName = target.site.alias.firstOrNull()
        val input = target.identifier

        val notice = origin.embed("Searching for $typeName **$input**.").awaitSingle()
        val censusId = try {
            getCensusId(ps2Target, input)
        } catch(e: Exception) {
            LOG.warn("Error calling PS2 API: ${e.message}")
            LOG.debug(e.stackTraceString)
            return
        }
        if(censusId == null) {
            notice.edit { spec -> spec.setEmbed { embed ->
                errorColor(embed)
                embed.setDescription("Unable to find PS2 $typeName **$input**.")
            } }.awaitSingle()
            return
        }

        propagateTransaction {
            val existing = PS2Tracks.TrackTarget.getExisting(censusId, origin.chan.id.asLong())
            if(existing == null) {
                notice.edit { spec -> spec.setEmbed { embed ->
                    errorColor(embed)
                    embed.setDescription("$typeName **$input** is not tracked in this channel.")
                } }.awaitSingle()
                return@propagateTransaction
            }

            existing.delete()
            notice.edit { spec -> spec.setEmbed { embed ->
                fbkColor(embed)
                embed.setDescription("No longer tracking $typeName **$input**.")
            } }.awaitSingle()
        }
    }

    private suspend fun getCensusId(target: PS2Target, id: String): String? {
        return when (target) {
            is PS2Target.Outfit -> when (target) {
                is PS2Target.Outfit.OutfitByName -> PS2Parser.searchOutfitByName(id)?.outfitId
                is PS2Target.Outfit.OutfitByTag -> PS2Parser.searchOutfitByTag(id)?.outfitId
                is PS2Target.Outfit.OutfitById -> error("Invalid track type outfit:id")
            }
            is PS2Target.OutfitCaptures -> PS2Parser.searchOutfitByTag(id)?.outfitId
            is PS2Target.ContinentEvent -> PS2Parser.searchServerByName(id)?.worldIdStr
            is PS2Target.Player -> PS2Parser.searchPlayerByName(id)?.characterId
        }
    }
}
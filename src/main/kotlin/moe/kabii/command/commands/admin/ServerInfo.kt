package moe.kabii.command.commands.admin

import discord4j.common.util.TimestampFormat
import discord4j.core.`object`.entity.Guild
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.verifyBotAdmin
import moe.kabii.discord.pagination.PaginationUtil

object ServerInfo : Command("servers") {
    override val wikiPath: String? = null

    init {
        chat {

            event.verifyBotAdmin()

            // build list of guilds
            val guildInfo = handler.instances.all()
                // transform from instance->list<guild> to list<guild, instance id>
                .flatMap { fbk ->
                    // convert each instance into instance # + list of guilds
                    val guilds = fbk.client.guilds.collectList().awaitSingle()
                    guilds.map { guild -> guild to fbk.clientId }
                }
                // one list of guilds, associated with the instance id here
                // sort by member count, then by which instance
                .sortedWith(
                    Comparator
                        .comparing<Pair<Guild, Int>, Int> { (guild, _) -> guild.memberCount }
                        .reversed() // largest guilds first
                        .thenComparingInt { (_, instanceId) -> instanceId }
                )
                .map { (guild, instanceId) ->
                    "#${instanceId} ${guild.memberCount}: ${guild.name} (${TimestampFormat.RELATIVE_TIME.format(guild.joinTime)}) ${guild.id.asString()}"
                }
            PaginationUtil.paginateListAsDescription(this, guildInfo, "List Guilds")
        }
    }
}
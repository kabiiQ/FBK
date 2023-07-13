package moe.kabii.command.commands.admin

import discord4j.common.util.TimestampFormat
import discord4j.core.`object`.entity.Guild
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.verifyBotAdmin
import moe.kabii.discord.pagination.PaginationUtil
import reactor.kotlin.core.publisher.toFlux

object ServerInfo : Command("servers") {
    override val wikiPath: String? = null

    init {
        chat {

            event.verifyBotAdmin()
            // build list of guilds
            val guildInfo = handler.instances.all().toFlux()
                .flatMap { fbk -> fbk.client.guilds }
                .sort(Comparator.comparing(Guild::getMemberCount).reversed())
                .map { guild ->
                    val guildName = guild.name
                        .replace("|", "\\|")
                    "${guild.memberCount}: $guildName (${TimestampFormat.RELATIVE_TIME.format(guild.joinTime)}) ${guild.id.asString()}"
                }
                .collectList()
                .awaitSingle()
            PaginationUtil.paginateListAsDescription(this, guildInfo, "List Guilds")
        }
    }
}
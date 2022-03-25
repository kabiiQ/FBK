package moe.kabii.command.commands.configuration

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.discord.util.BotUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait
import moe.kabii.util.extensions.userAddress
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.sql.transactions.transaction

object Preferences : CommandContainer {
    object Guild : Command("setguild","set-guild", "set-server", "myguild") {
        override val wikiPath: String? = null // intentionally undocumented command

        init {
            discord {
                val guildTarget = when {
                    args.isNotEmpty() -> {
                        val id = args[0].toLongOrNull()?.snowflake
                        if (id != null) {
                            try {
                                event.client.getGuildById(id).awaitSingle()
                            } catch (ce: ClientException) {
                                reply(Embeds.error("Unknown guild ID **${id.asString()}** specified.")).subscribe()
                                return@discord
                            }
                        } else {
                            reply(Embeds.error("Invalid guild ID **${args[0]}** specified.")).subscribe()
                            return@discord
                        }
                    }
                    guild != null -> guild  // only post personalized embed in DMs. if ran in a guild, this is the new target
                    else -> {
                        val mutual = BotUtil.getMutualGuilds(author).collectList().tryAwait().orNull()
                        if (mutual.isNullOrEmpty()) null
                        else {
                            // embed to prompt user to select a server
                            val serversLong = mutual.mapIndexed { guildIndex, guild ->
                                val index = guildIndex + 1
                                "$index: ${guild.name}"
                            }.joinToString("\n")
                            val servers = StringUtils.abbreviate(serversLong, MagicNumbers.Embed.MAX_DESC)
                            val botAvatar = event.client.self.map(User::getAvatarUrl).tryAwait().orNull()
                            val prompt = reply(
                                Embeds.fbk(servers)
                                    .withAuthor(EmbedCreateFields.Author.of("Mutual Servers with ${author.userAddress()}:", null, botAvatar))
                                    .withTitle("Select a server number to set as the target for DM commands or \"exit\" to cancel.")
                            ).awaitSingle()
                            getLong(1..mutual.size.toLong(), prompt, timeout = 90000L)?.let { response -> mutual[response.toInt() - 1] }
                        }
                    }
                }
                if(guildTarget == null) {
                    usage("The **setguild** command should be either: executed in the desired target guild, provided with the target guild ID, or ran in DMs and the bot will grab your mutual servers.", "setguild (guildID)").awaitSingle()
                    return@discord
                }
                transaction {
                    val user = DiscordObjects.User.getOrInsert(author.id.asLong())
                    user.target = guildTarget.id.asLong()
                }
                reply(Embeds.fbk("Commands through PM will target the guild **${guildTarget.name}**.")).awaitSingle()
            }
        }
    }
}
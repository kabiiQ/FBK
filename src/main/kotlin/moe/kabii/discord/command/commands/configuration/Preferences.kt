package moe.kabii.discord.command.commands.configuration

import discord4j.core.`object`.entity.User
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.relational.DiscordObjects
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.util.BotUtil
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryAwait
import org.jetbrains.exposed.sql.transactions.transaction

object Preferences : CommandContainer {
    object Guild : Command("setguild","set-guild", "set-server", "myguild") {
        init {
            discord {
                val guildTarget = when {
                    args.isNotEmpty() -> {
                        val id = args[0].toLongOrNull()?.snowflake
                        if (id != null) {
                            try {
                                event.client.getGuildById(id).awaitSingle()
                            } catch (ce: ClientException) {
                                error("Unknown guild ID **${id.asString()}** specified.").subscribe()
                                return@discord
                            }
                        } else {
                            error("Invalid guild ID **${args[0]}** specified.").subscribe()
                            return@discord
                        }
                    }
                    guild != null -> guild  // only post personalized embed in DMs. if ran in a guild, this is the new target
                    else -> {
                        val mutual = BotUtil.getMutualGuilds(author).collectList().tryAwait().orNull()
                        if (mutual.isNullOrEmpty()) null
                        else {
                            // embed to prompt user to select a server
                            val servers = mutual.mapIndexed { index, guild ->
                                val index = index + 1
                                "$index: ${guild.name}"
                            }.joinToString("\n").take(1900)
                            val botAvatar = event.client.self.map(User::getAvatarUrl).tryAwait().orNull()
                            val prompt = embed {
                                setAuthor("Mutual Servers with ${author.username}#${author.discriminator}:", null, botAvatar)
                                setTitle("Select a server number to set as the target for DM commands or \"exit\" to cancel.")
                                setDescription(servers)
                            }.awaitSingle()
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
                embed("Commands through PM will target the guild **${guildTarget.name}**.").awaitSingle()
            }
        }
    }
}
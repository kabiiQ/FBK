package moe.kabii.command.commands.utility

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.DateValidation
import moe.kabii.discord.util.Search
import moe.kabii.discord.util.SnowflakeParser
import moe.kabii.net.NettyFileServer
import java.io.File
import java.time.format.DateTimeFormatter

object SnowflakeUtil : CommandContainer {
    object GetIDs : Command("ids", "getids", "allids", "roleids") {
        override val wikiPath by lazy { TODO() }

        init {
            discord {
                val includeAll = args.getOrNull(0)?.endsWith("all") ?: false
                val all = if(includeAll) "-all" else ""
                val targetFile = File(NettyFileServer.idRoot, "${target.id.asString()}$all.txt")
                val warning = if (includeAll) {
                    chan.createMessage("Getting ALL IDs may take a minute for a large guild. Working...").awaitSingle()
                } else null
                val output = StringBuilder()
                output.append("IDs for guild ${target.name}\n\nRoles:\n")
                target.roles
                    .collectList()
                    .awaitSingle()
                    .reversed()
                    .forEach { role ->
                        output.append(role.name)
                            .append(": ")
                            .append(role.id.asString())
                            .append('\n')
                    }

                if (includeAll) {
                    // add channels and users
                    output.append("\n\nChannels: \n")
                    target.channels
                        .collectList()
                        .awaitSingle()
                        .forEach { channel ->
                            output.append("#${channel.name}")
                                .append(": ")
                                .append(channel.id.asString())
                                .append('\n')
                        }
                    output.append("\n\nUsers: \n")
                    target.members
                        .collectList()
                        .awaitSingle()
                        .forEach { user ->
                            output.append(user.username)
                            user.nickname.ifPresent {
                                output.append(" (")
                                    .append(it)
                                    .append(")")
                            }
                            output.append(": ")
                                .append(user.id.asString())
                                .append('\n')
                        }

                }
                targetFile.writeText(output.toString())
                val url = if(includeAll) NettyFileServer.idsAll(target.id.asString()) else NettyFileServer.ids(target.id.asString())
                warning?.delete()?.subscribe()
                embed("[IDs for all roles]($url)").awaitSingle()
            }
        }
    }

    private val formatter = DateTimeFormatter.ofPattern("MMMM dd yyyy @ HH:mm:ss 'UTC'")
    object Timestamp : Command("timestamp", "snowflakedate", "checktimestamp", "gettimestamp", "timeof", "timestampof", "snowflaketime") {
        override val wikiPath = "Discord-Info-Commands#-get-the-timestamp-for-any-discord-id-snowflake"

        init {
            discord {
                // get the timestamp for a snowflake
                if(args.isEmpty()) {
                    usage("This command gets the timestamp inside any Discord snowflake.", "timestamp <snowflake>").awaitSingle()
                    return@discord
                }
                val id = args[0].toLongOrNull()
                if(id == null) {
                    error("**${args[0]}** is not a valid snowflake. Discord snowflakes are 17-18 digit integers.").awaitSingle()
                    return@discord
                }
                val snowflake = SnowflakeParser.of(id)
                val validation = when(snowflake.valiDate) {
                    DateValidation.NEW -> "\nThis snowflake is not valid: it represents a future date."
                    DateValidation.OLD -> "\nThis snowflake is not valid! It represents a date before the Discord epoch (2015)."
                    else -> null
                }.orEmpty()

                val formatted = formatter.format(snowflake.utc)
                embed {
                    setDescription("${validation}The snowflake **$id** would represent a Discord object created:\n**$formatted**")
                    setFooter("Localized timestamp", null)
                    setTimestamp(snowflake.instant)
                }.awaitSingle()
            }
        }
    }

    object GetID : Command("id") {
        override val wikiPath by lazy { TODO() }

        init {
            discord {
                // get an ID for a user. for noobs without developer mode enabled.
                if(args.isEmpty()) {
                    usage("**id** can be used to find the Discord ID for a user in your server.", "id <username>")
                }
                val targetUser = Search.user(this, noCmd, target)
                if(targetUser == null) {
                    error("Unable to find user **$noCmd**.")
                    return@discord
                }
                embed {
                    setAuthor("${targetUser.username}#${targetUser.discriminator}", null, targetUser.avatarUrl)
                    setDescription("ID: ${targetUser.id.asString()}")
                }.awaitSingle()
            }
        }
    }
}
package moe.kabii.discord.command.commands.utility

import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.util.DateValidation
import moe.kabii.discord.util.SnowflakeParser
import moe.kabii.net.NettyFileServer
import java.io.File
import java.time.format.DateTimeFormatter

object SnowflakeUtil : CommandContainer {
    object GetIDs : Command("ids", "getids", "allids", "roleids") {
        init {
            discord {
                val includeAll = args.getOrNull(0)?.endsWith("all") ?: false
                val all = if(includeAll) "-all" else ""
                val targetFile = File(NettyFileServer.idRoot, "${target.id.asString()}$all.txt")
                val warning = if (includeAll) {
                    chan.createMessage("Getting ALL IDs may take a minute for a large guild. Working...").block()
                } else null
                val output = StringBuilder()
                output.append("IDs for guild ${target.name}\n\nRoles:\n")
                target.roles
                    .collectList()
                    .block()
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
                        .block()
                        .forEach { channel ->
                            output.append("#${channel.name}")
                                .append(": ")
                                .append(channel.id.asString())
                                .append('\n')
                        }
                    output.append("\n\nUsers: \n")
                    target.members
                        .collectList()
                        .block()
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
                embed("[IDs for all roles]($url)").block()
            }
        }
    }

    private val formatter = DateTimeFormatter.ofPattern("MMMM dd yyyy @ HH:mm:ss 'UTC'")
    object Timestamp : Command("snowflake", "timestamp", "snowflakedate", "checktimestamp") {
        init {
            discord {
                // get the timestamp for a snowflake
                if(args.isEmpty()) {
                    usage("This command gets the timestamp inside any Discord snowflake.", "snowflake <snowflake>").block()
                    return@discord
                }
                val id = args[0].toLongOrNull()
                if(id == null) {
                    error("**${args[0]}** is not a valid snowflake. Discord snowflakes are 17-18 digit integers.").block()
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
                }.block()
            }
        }
    }

    // just a 1:1 of discord snowflake, not intended to be useful info
    object SnowflakeDetail : Command("snowflakedetail", "snowflakedetails", "analyzesnowflake", "snowflakeinfo", "snowflakebreakdown", "explainsnowflake", "snowflakeexplain") {
        init {
            discord {
                if (args.isEmpty()) {
                    usage("This command breaks the components of a Discord snowflake.", "snowflakedetail <snowflake>").block()
                    return@discord
                }
                val id = args[0].toLongOrNull()
                if (id == null) {
                    error("**${args[0]}** is not a valid snowflake. Discord snowflakes are 17-18 digit integers.").block()
                    return@discord
                }
                val snowflake = SnowflakeParser.of(id)
                embed {
                    setDescription("Technical breakdown of the snowflake **$id**:")
                    addField("Timestamp", "${snowflake.timestamp}: the milliseconds since the Discord epoch (the first second of 2015)", false)
                    addField("Internal Worker ID", snowflake.workerID.toString(), false)
                    addField("Internal process ID", snowflake.processID.toString(), false)
                    addField("Increment", "${snowflake.increment}: the number of snowflakes previously generated on process #${snowflake.processID}", false)
                }.block()
            }
        }
    }
}
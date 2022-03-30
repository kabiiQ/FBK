package moe.kabii.command.commands.utility

import discord4j.common.util.TimestampFormat
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.DateValidation
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.discord.util.SnowflakeParser
import moe.kabii.net.NettyFileServer
import java.io.File

object SnowflakeUtil : CommandContainer {
    object GetIDs : Command("ids", "getids", "allids", "roleids") {
        override val wikiPath = "Discord-Info-Commands#get-all-ids-in-a-server"

        init {
            discord {
                val targetId = target.id.asString()
                val targetFile = File(NettyFileServer.idRoot, "$targetId.txt")
                val warning = chan.createMessage("Gathering IDs...").awaitSingle()
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

                targetFile.writeText(output.toString())
                val url = NettyFileServer.ids(targetId)
                warning?.delete()?.subscribe()
                send(Embeds.fbk("[List of IDs for ${target.name}]($url)")).awaitSingle()
            }
        }
    }

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
                    send(Embeds.error("**${args[0]}** is not a valid snowflake. Discord snowflakes are 17-18 digit integers.")).awaitSingle()
                    return@discord
                }
                val snowflake = SnowflakeParser.of(id)
                val validation = when(snowflake.valiDate) {
                    DateValidation.NEW -> "\nThis snowflake is not valid: it represents a future date."
                    DateValidation.OLD -> "\nThis snowflake is not valid! It represents a date before the Discord epoch (2015)."
                    else -> null
                }.orEmpty()

                val formatted = TimestampFormat.LONG_DATE_TIME.format(snowflake.instant)
                send(Embeds.fbk("${validation}The snowflake **$id** would represent a Discord object created: **$formatted**")).awaitSingle()
            }
        }
    }

    object GetID : Command("id") {
        override val wikiPath = "Discord-Info-Commands#get-a-users-discord-id"

        init {
            discord {
                if(args.isEmpty()) {
                    usage("**id** can be used to find the Discord ID for a user in your server.", "id <username>")
                    return@discord
                }
                val targetUser = Search.user(this, noCmd, target)
                if(targetUser == null) {
                    send(Embeds.error("Unable to find user **$noCmd**.")).awaitSingle()
                    return@discord
                }
                send(Embeds.fbk("ID: ${targetUser.id.asString()}", targetUser)).awaitSingle()
            }
        }
    }
}
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
import moe.kabii.util.extensions.awaitAction
import java.io.File

object SnowflakeUtil : CommandContainer {
    object GetIDs : Command("ids") {
        override val wikiPath = "Discord-Info-Commands#get-all-ids-in-a-server"

        init {
            discord {
                val targetId = target.id.asString()
                val targetFile = File(NettyFileServer.idRoot, "$targetId.txt")
                event.deferReply().awaitAction()
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

                if(args.optBool("IncludeUsers") == true) {

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
                val url = NettyFileServer.ids(targetId)
                event.editReply()
                    .withEmbeds(Embeds.fbk("[List of IDs for ${target.name}]($url)"))
                    .awaitSingle()
            }
        }
    }

    object Timestamp : Command("timestamp") {
        override val wikiPath = "Discord-Info-Commands#-get-the-timestamp-for-any-discord-id-snowflake"

        init {
            discord {
                // get the timestamp for a snowflake
                val idArg = args.int("DiscordID")
                val snowflake = SnowflakeParser.of(idArg)
                val validation = when(snowflake.valiDate) {
                    DateValidation.NEW -> "\nThis snowflake is not valid: it represents a future date."
                    DateValidation.OLD -> "\nThis snowflake is not valid! It represents a date before the Discord epoch (2015)."
                    else -> null
                }.orEmpty()

                val formatted = TimestampFormat.LONG_DATE_TIME.format(snowflake.instant)
                ireply(Embeds.fbk("${validation}The snowflake **$idArg** would represent a Discord object created: **$formatted**")).awaitSingle()
            }
        }
    }

    object GetID : Command("id") {
        override val wikiPath = "Discord-Info-Commands#get-a-users-discord-id"

        init {
            discord {
                val targetUser = args.optUser("user")?.awaitSingle() ?: author
                ereply(Embeds.fbk("ID: ${targetUser.id.asString()}", targetUser)).awaitSingle()
            }
        }
    }
}
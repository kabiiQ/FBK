package moe.kabii.command.commands.utility

import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.verify
import moe.kabii.structure.extensions.snowflake

object StarboardUtil : CommandContainer {
    object StarMessage : Command("starmessage", "addstar", "starboardmessage") {
        override val wikiPath: String?
            get() = TODO()

        init {
            discord {
                member.verify(Permission.MANAGE_MESSAGES)

                val starboardCfg = config.starboard
                if(starboardCfg == null) {
                    error("**${target.name}** does not have a starboard to add a message to. See the **starboard set** command to create a starboard for this server.").awaitSingle()
                    return@discord
                }

                // starmessage <message id>
                if(args.size != 1) {
                    usage("**starmessage** is used to instantly add a message to your server's starboard.", "starmessage <Discord message ID>").awaitSingle()
                    return@discord
                }

                val targetId = args[0].toLongOrNull()?.snowflake
                if(targetId == null) {
                    usage("Invalid message ID **${args[0]}**.", "starmessage <Discord message ID>").awaitSingle()
                    return@discord
                }

                val targetMessage = try {
                    chan.getMessageById(targetId).awaitSingle()
                } catch(ce: ClientException) {
                    error("Unable to find a message with ID **$targetId**.").awaitSingle()
                    return@discord
                }

                if(starboardCfg.findAssociated(targetId.asLong()) != null) {
                    error("Message **${targetId}** is already starboarded.").awaitSingle()
                    return@discord
                }

                val starboard = starboardCfg.asStarboard(target, config)
                starboard.addToBoard(targetMessage, mutableSetOf(), exempt = true)

            }
        }
    }
}
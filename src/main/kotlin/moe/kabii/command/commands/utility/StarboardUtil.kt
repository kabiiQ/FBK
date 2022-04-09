package moe.kabii.command.commands.utility

import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.verify
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.snowflake

object StarboardUtil : CommandContainer {
    object StarMessage : Command("starmessage") {
        override val wikiPath = "Starboard#manually-adding-a-message-to-the-starboard-starmessage"

        init {
            discord {
                member.verify(Permission.MANAGE_MESSAGES)

                val starboardCfg = config.starboardSetup
                if(starboardCfg.channel == null) {
                    ereply(Embeds.error("**${target.name}** does not have a starboard to add a message to. See the **/starboard** command to create a starboard for this server.")).awaitSingle()
                    return@discord
                }

                val messageArg = args.string("message")
                val messageId = messageArg.toLongOrNull()?.snowflake

                if(messageId == null) {
                    ereply(Embeds.error("Invalid Discord message ID **$messageArg**.")).awaitSingle()
                    return@discord
                }

                val targetMessage = try {
                    chan.getMessageById(messageId).awaitSingle()
                } catch(ce: ClientException) {
                    ereply(Embeds.error("Unable to find the message with ID **$messageArg** in ${guildChan.name}.")).awaitSingle()
                    return@discord
                }

                if(starboardCfg.findAssociated(messageId.asLong()) != null) {
                    ereply(Embeds.error("Message **$messageArg** is already starboarded.")).awaitSingle()
                    return@discord
                }

                val starboard = starboardCfg.asStarboard(target, config)
                starboard.addToBoard(targetMessage, mutableSetOf(), exempt = true)
            }
        }
    }
}
package moe.kabii.discord.command.commands.moderation

import discord4j.core.`object`.util.Permission
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.PermissionUtil
import moe.kabii.discord.command.errorColor
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.net.NettyFileServer
import moe.kabii.structure.EmbedReceiver
import moe.kabii.structure.tryBlock
import moe.kabii.util.ColorUtil
import moe.kabii.util.RGB
import java.awt.Color

object RandomRoleColor : Command("randomcolor", "randomizecolor", "newcolor") {
    private fun randomColor() = Color((0..0xFFFFFF).random())

    init {
        botReqs(Permission.MANAGE_ROLES)
        discord {
            member.verify(Permission.MANAGE_ROLES)
            if (args.isEmpty()) {
                usage("**randomcolor** will pick a random color for a role.", "randomcolor <role name or ID>").block()
                return@discord
            }

            val role = Search.roleByNameOrID(this, noCmd)
            if (role == null) {
                usage("Unable to find the role **$noCmd**.", "randomcolor <role name or ID>").block()
                return@discord
            }
            val safe = PermissionUtil.isSafeRole(role, member, target, managed = true, everyone = false)
            if(!safe) {
                error("You can not manage the role **${role.name}**.").block()
                return@discord
            }
            fun colorPicker(color: Color): EmbedReceiver = {
                setColor(color)
                val rgb = RGB(color)
                val (r, g, b) = rgb
                setAuthor("Role Color Randomizer for \"${role.name}\"", null, null)
                setThumbnail(NettyFileServer.rgb(rgb))
                setDescription("Color: ($r, $g, $b)")
            }

            var currColor = randomColor()
            var hex = ColorUtil.hexString(currColor)
            val prompt = embed(colorPicker(currColor)).block()
            var first = true
            loop@while(true) {
                // y/n /exit
                val response = getBool(prompt, timeout = 240000L, add = first)
                first = false
                when (response) {
                    true -> { // set color
                        val oldColor = ColorUtil.hexString(role.color)
                        val edit = role.edit { role ->
                            role.setColor(currColor)
                        }.tryBlock().orNull()
                        if (edit != null) {
                            prompt.edit { message ->
                                message.setEmbed { embed ->
                                    embed.setDescription("**${role.name}**'s color has been changed to $hex. (Previously $oldColor)")
                                    embed.setColor(currColor)
                                }
                            }.tryBlock()
                        } else {
                            error("I am unable to edit the role **${role.name}**. The hex value for the color you wanted to set was $hex.").tryBlock()
                            prompt.edit { message ->
                                message.setEmbed { embed ->
                                    embed.setDescription("I am unable to edit the role **${role.name}**. The hex value for the color you wanted to set was $hex.")
                                    errorColor(embed)
                                }
                            }.tryBlock()
                        }
                        break@loop
                    }
                    false -> { // new color
                        currColor = randomColor()
                        hex = ColorUtil.hexString(currColor)
                        prompt.edit { message ->
                            message.setEmbed(colorPicker(currColor))
                        }.block()
                    }
                    null -> break@loop // exit
                }
            }
            prompt.removeAllReactions().subscribe()
        }
    }
}
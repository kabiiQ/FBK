package moe.kabii.command.commands.moderation

import discord4j.rest.util.Color
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.PermissionUtil
import moe.kabii.command.errorColor
import moe.kabii.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.net.NettyFileServer
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.tryAwait
import moe.kabii.util.ColorUtil
import moe.kabii.util.RGB

object RandomRoleColor : Command("randomcolor", "randomizecolor", "newcolor") {
    private fun randomColor() = Color.of((0..0xFFFFFF).random())

    init {
        botReqs(Permission.MANAGE_ROLES)
        discord {
            member.verify(Permission.MANAGE_ROLES)
            if (args.isEmpty()) {
                usage("**randomcolor** will pick a random color for a role.", "randomcolor <role name or ID>").awaitSingle()
                return@discord
            }

            val role = Search.roleByNameOrID(this, noCmd)
            if (role == null) {
                usage("Unable to find the role **$noCmd**.", "randomcolor <role name or ID>").awaitSingle()
                return@discord
            }
            val safe = PermissionUtil.isSafeRole(role, member, target, managed = true, everyone = false)
            if(!safe) {
                error("You can not manage the role **${role.name}**.").awaitSingle()
                return@discord
            }
            fun colorPicker(color: Color): EmbedBlock = {
                setColor(color)
                val rgb = RGB(color)
                val (r, g, b) = rgb
                setAuthor("Role Color Randomizer for \"${role.name}\"", null, null)
                setThumbnail(NettyFileServer.rgb(rgb))
                setDescription("Color: ($r, $g, $b)")
            }

            var currColor = randomColor()
            var hex = ColorUtil.hexString(currColor)
            val prompt = embedBlock(colorPicker(currColor)).awaitSingle()
            var first = true
            loop@while(true) {
                // y/n /exit
                val response = getBool(prompt, timeout = 240000L, add = first)
                first = false
                when (response) {
                    true -> { // set color
                        val oldColor = ColorUtil.hexString(role.color)
                        val edit = role.edit { spec ->
                            spec.setColor(currColor)
                        }.tryAwait().orNull()
                        if (edit != null) {
                            prompt.edit { message ->
                                message.setEmbed { embed ->
                                    embed.setDescription("**${role.name}**'s color has been changed to $hex. (Previously $oldColor)")
                                    embed.setColor(currColor)
                                }
                            }.tryAwait()
                        } else {
                            error("I am unable to edit the role **${role.name}**. The hex value for the color you wanted to set was $hex.").tryAwait()
                            prompt.edit { message ->
                                message.setEmbed { embed ->
                                    embed.setDescription("I am unable to edit the role **${role.name}**. The hex value for the color you wanted to set was $hex.")
                                    errorColor(embed)
                                }
                            }.tryAwait()
                        }
                        break@loop
                    }
                    false -> { // new color
                        currColor = randomColor()
                        hex = ColorUtil.hexString(currColor)
                        prompt.edit { message ->
                            message.setEmbed(colorPicker(currColor))
                        }.awaitSingle()
                    }
                    null -> break@loop // exit
                }
            }
            prompt.removeAllReactions().subscribe()
        }
    }
}
package moe.kabii.command.commands.moderation

import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.PermissionUtil
import moe.kabii.command.verify
import moe.kabii.discord.util.ColorUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.RGB
import moe.kabii.discord.util.Search
import moe.kabii.net.NettyFileServer
import moe.kabii.util.extensions.tryAwait

object RandomRoleColor : Command("randomcolor", "randomizecolor", "newcolor") {
    override val wikiPath = "Moderation-Commands#randomizing-a-roles-color"

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
                send(Embeds.error("You can not manage the role **${role.name}**.")).awaitSingle()
                return@discord
            }
            fun colorPicker(color: Color): EmbedCreateSpec {
                val rgb = RGB(color)
                val (r, g, b) = rgb
                return Embeds.other("Color: ($r, $g, $b)", color)
                    .withAuthor(EmbedCreateFields.Author.of("Role Color Randomizer for \"${role.name}\"", null, null))
                    .withThumbnail(NettyFileServer.rgb(rgb))
            }

            var currColor = randomColor()
            var hex = ColorUtil.hexString(currColor)
            val prompt = send(colorPicker(currColor)).awaitSingle()

            var first = true
            loop@while(true) {
                // y/n /exit
                val response = getBool(prompt, timeout = 240000L, add = first)
                first = false
                when (response) {
                    true -> { // set color
                        val oldColor = ColorUtil.hexString(role.color)
                        val edit = role.edit().withColor(currColor).tryAwait().orNull()
                        if (edit != null) {
                            prompt.edit()
                                .withEmbeds(Embeds.other("**${role.name}**'s color has been changed to $hex. (Previously $oldColor)", currColor))
                                .tryAwait()
                        } else {
                            send(Embeds.error("I am unable to edit the role **${role.name}**. The hex value for the color you wanted to set was $hex.")).tryAwait()
                            prompt.edit()
                                .withEmbeds(Embeds.error("I am unable to edit the role **${role.name}**. I must have a role above **${role.name}** to to edit it. The hex value for the color you wanted to set was $hex."))
                                .awaitSingle()
                        }
                        break@loop
                    }
                    false -> { // new color
                        currColor = randomColor()
                        hex = ColorUtil.hexString(currColor)
                        prompt.edit()
                            .withEmbeds(colorPicker(currColor))
                            .awaitSingle()
                    }
                    null -> break@loop // exit
                }
            }
            prompt.removeAllReactions().subscribe()
        }
    }
}
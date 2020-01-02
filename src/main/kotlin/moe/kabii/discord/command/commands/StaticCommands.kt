package moe.kabii.discord.command.commands

import discord4j.core.`object`.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import java.io.FileInputStream

object StaticCommands : CommandContainer {
    object Smug : Command("smug", "kizunaai", "kizuna") {
        init {
            botReqs(Permission.ATTACH_FILES)
            discord {
                FileInputStream("files/images/KizunaAi_Smug.png").use { smugStream ->
                    chan.createMessage { spec ->
                        spec.addFile("smug.png", smugStream)
                    }
                }.awaitSingle()
            }
        }
    }
}
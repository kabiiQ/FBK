package moe.kabii.command.commands.moderation

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.verify
import moe.kabii.structure.success
import moe.kabii.structure.tryAwait

object PruneUsers : Command("prunemembers") {
    override val wikiPath by lazy { TODO() }

    init {
        botReqs(Permission.KICK_MEMBERS)
        discord {
            member.verify(Permission.KICK_MEMBERS, Permission.MANAGE_GUILD)

            val prompt = embed("This action will kick all members from your server who do not currently have a role. This is probably NOT what you want to do unless converting to a sub-only server. Please check that the bot has the \"kick members\" permission and confirm.").awaitSingle()
            val response = getBool(prompt)

            if(response == true) {
                val targets = target.members
                    .filter { member -> member.roleIds.isEmpty() }
                    .collectList().awaitSingle()

                // make really really sure
                val confirmPrompt = embed("This will really kick **${targets.size}** members from **${target.name}**! Please confirm.").awaitSingle()
                val confirmation = getBool(confirmPrompt)

                if(confirmation == true) {
                    // execute
                    embed("Performing prune...").awaitSingle()

                    val results =  targets.map { member ->
                        member.kick("Member purge command executed by user: ${author.id.asString()}").success()
                            .tryAwait()
                    }

                    var total = 0
                    val success = results.count {
                        total++
                        it.orNull() == true
                    }

                    embed("Prune complete! Successfully kicked **$success/$total** users from **${target.name}**").awaitSingle()
                    confirmPrompt.delete().success().awaitSingle()
                }
            }

            // always delete prompt
            prompt.delete().success().awaitSingle()
        }
    }
}
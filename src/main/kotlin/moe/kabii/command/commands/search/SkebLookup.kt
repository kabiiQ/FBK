package moe.kabii.command.commands.search

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds
import moe.kabii.search.skeb.SkebIOException
import moe.kabii.search.skeb.SkebParser
import moe.kabii.util.constants.EmojiCharacters

object SkebLookup : Command("skeb") {

    override val wikiPath = "Lookup-Commands#skeb-profile-lookup-skeb"

    init {
        chat {
            val usernameArg = args.string("username")
            val skebber = try {
                val user = SkebParser.getUser(usernameArg)
                if(user == null) {
                    ireply(Embeds.error("Unable to find skeb user **$usernameArg**.")).awaitSingle()
                    return@chat
                } else user
            } catch(e: SkebIOException) {
                ereply(Embeds.error("Unable to reach Skeb at this time.")).awaitSingle()
                return@chat
            }

            val profileUrl = "https://skeb.jp/@${skebber.username}"
            val desc = StringBuilder()
            skebber.username.run { desc.append("Skeb profile: [@$this](https://skeb.jp/@$this)\n") }
            skebber.twitterName?.run { desc.append("Twitter: [@$this](https://twitter.com/$this)\n") }
            skebber.language?.run { desc.append("Language: $this\n") }
            if(skebber.sentRequests > 0) desc.append("User Requested Skebs: ${skebber.sentRequests}\n")

            fun flag(flag: Boolean) = if(flag) EmojiCharacters.checkBox else EmojiCharacters.redX
            if(skebber.creator) {
                desc.append("\nNSFW requests: ${flag(skebber.nsfw)}\n")
                desc.append("Private requests: ${flag(skebber.private)}\n")
                desc.append("Genre: ${skebber.genre}\n")
                desc.append("Received requests: ${skebber.receivedRequests}\n")
                desc.append("Recommended price: JPY**${skebber.defaultAmount ?: "Unknown"}**\n")
                desc.append("Currently accepting requests: ${flag(skebber.accepting)}")
            } else desc.append("@${skebber.username} is not a skeb creator.")

            ireply(
                Embeds.fbk(desc.toString())
                    .withAuthor(EmbedCreateFields.Author.of(skebber.name, profileUrl, skebber.avatarUrl))
                    .run { if(skebber.header != null) withImage(skebber.header) else this }
            ).awaitSingle()
        }
    }

}
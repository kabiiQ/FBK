package moe.kabii.command.commands.search

import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.search.skeb.SkebIOException
import moe.kabii.discord.search.skeb.SkebParser
import moe.kabii.util.constants.EmojiCharacters

object SkebLookup : Command("skeb") {

    override val wikiPath: String? = null // TODO

    init {
        discord {
            channelFeatureVerify(FeatureChannel::searchCommands, "search")

            if(args.isEmpty()) {
                usage("**skeb** pulls information on a user's skeb.jp profile.", "skeb <skeb username>").awaitSingle()
                return@discord
            }

            val targetUsername = args[0].removePrefix("@")
            val skebber = try {
                val user = SkebParser.getUser(targetUsername)
                if(user == null) {
                    error("Unable to find skeb user **$targetUsername**.").awaitSingle()
                    return@discord
                } else user
            } catch(e: SkebIOException) {
                error("Unable to reach Skeb at this time.").awaitSingle()
                return@discord
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
                desc.append("Recommended price: JPY${skebber.defaultAmount ?: "Unknown"}\n")
                desc.append("Currently accepting requests: ${flag(skebber.accepting)}")
            } else desc.append("@${skebber.username} is not a skeb creator.")
            embed {
                setAuthor(skebber.name, profileUrl, skebber.avatarUrl)
                setDescription(desc.toString())
                if(skebber.header != null) setImage(skebber.header)
            }.awaitSingle()
        }
    }

}
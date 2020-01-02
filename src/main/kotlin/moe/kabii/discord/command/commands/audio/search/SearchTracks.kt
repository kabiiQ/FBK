package moe.kabii.discord.command.commands.audio.search

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.audio.FallbackHandler
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.commands.audio.AudioCommandContainer
import moe.kabii.structure.tryAwait

object SearchTracks : AudioCommandContainer {
    object SearchSource : Command("search", "select", "selectfrom") {
        init {
            discord {
                // search source query
                validateChannel(this)
                if(!validateVoice(this)) {
                    error("You must be in the bot's voice channel if the bot is in use.").awaitSingle()
                    return@discord
                }
                if(args.isEmpty()) {
                    usage("**search** is used to search a source for audio to play. You can provide the source (currently the only options are YouTube [yt] or SoundCloud [sc]) as the first argument, or YouTube will automatically be used.", "search (source) <query>").awaitSingle()
                    return@discord
                }
                val parse = AudioSource.parse(args[0])
                val (source, args) =
                    if(parse == null) AudioSource.YOUTUBE to args
                    else parse to args.drop(1) // if the first arg is a target, use it as the target instead of part of query
                val query = args.joinToString(" ")
                val search = source.handler.search(query)
                if(search.isEmpty()) {
                    error("No results found searching **${source.fullName}** for **$query**.").awaitSingle()
                    return@discord
                }
                // build search selection menu until 10 songs or 2000 chars
                val menu = StringBuilder()
                for(index in search.indices) {
                    val id = index + 1
                    val track = search[index]
                    val author = if(track.info.author != null) " Uploaded by ${track.info.author}" else ""
                    val entry = "$id. ${trackString(track, includeAuthor = false)}$author\n"
                    if(menu.length + entry.length > 1900) break
                    menu.append(entry)
                }
                // technically should keep track of which ones aren't printed but it's not a big deal if the user queues something that isn't displayed. we just can't send the name.
                val embed = embed {
                    setAuthor("Results from ${source.fullName} for \"$query\"", null, null)
                    setTitle("Select track to be played or \"exit\"")
                    setDescription(menu.toString())
                }.awaitSingle()
                val input = getLong(1..search.size.toLong(), embed, timeout = 150_000L)?.toInt()
                if(input != null) {
                    FallbackHandler(this).trackLoaded(search[input - 1])
                }
                embed.delete().tryAwait()
            }
        }
    }

    object PlayFromSource : Command("playfrom", "playfromsource", "usesource") {
        init {
            discord {
                validateChannel(this)
                if(!validateVoice(this)) {
                    error("You must be in the bot's voice channel if the bot is in use.").awaitSingle()
                    return@discord
                }
                if(args.size < 2) {
                    usage("**playfrom** is used to search and play the first result from a specific source. (Currently YouTube [yt] or SoundCloud [sc])", "playfrom <yt/sc> <query>").awaitSingle()
                    return@discord
                }
                val source = AudioSource.parse(args[0])
                if(source == null) {
                    error("Unknown source **${args[0]}**. Currently valid sources are YouTube (yt) or SoundCloud (sc).").awaitSingle()
                    return@discord
                }
                val query = args.drop(1).joinToString("")
                val search = source.handler.search(query)
                if(search.isEmpty()) {
                    error("No results found searching **${source.fullName}** for **$query**.").awaitSingle()
                    return@discord
                }
                FallbackHandler(this).trackLoaded(search[0])
            }
        }
    }
}
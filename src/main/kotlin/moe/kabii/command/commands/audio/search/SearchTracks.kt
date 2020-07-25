package moe.kabii.command.commands.audio.search

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.command.commands.audio.AudioStateUtil
import moe.kabii.command.commands.audio.ParseUtil
import moe.kabii.discord.audio.ExtractedQuery
import moe.kabii.discord.audio.FallbackHandler
import moe.kabii.structure.tryAwait

object SearchTracks : AudioCommandContainer {
    object SearchSource : Command("search", "select", "selectfrom") {
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                validateChannel(this)
                // search source query
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
                var selected = listOf<Int>()
                for(attempt in 0..25) { // after 25 messages or 2 minutes we'll stop listening
                    val input = getString(timeout = 120_000L)
                    if(input == null) break
                    if(input.isNotBlank()) {
                        val inputArgs = input.split(" ")
                        val (sel, _) = ParseUtil.parseRanges(search.size, inputArgs)
                        if(sel.isNotEmpty()) {
                            selected = sel

                            // join voice channel if not within
                            val voice = AudioStateUtil.checkAndJoinVoice(this)
                            if(voice is AudioStateUtil.VoiceValidation.Failure) {
                                error(voice.error).awaitSingle()
                                return@discord
                            }
                            break
                        }
                    }
                }
                val silent = selected.size > 1 // if multiple are selected, don't post a message for each one.
                selected.forEach { selection ->
                    val track = search[selection - 1]
                    // fallback handler = don't search or try to resolve a different track if videos is unavailable
                    FallbackHandler(this, extract = ExtractedQuery.default(track.identifier)).trackLoadedModifiers(track, silent = true)
                }
                embed.delete().tryAwait()
                if(silent) {
                    embed {
                        setAuthor("${author.username}#${author.discriminator}", null, author.avatarUrl)
                        setDescription("Adding **${selected.size}** tracks to queue.")
                    }.awaitSingle()
                }
            }
        }
    }

    object PlayFromSource : Command("playfrom", "playfromsource", "usesource") {
        override val wikiPath: String? = null // intentionally undocumented command

        init {
            discord {
                validateChannel(this)
                val voice = AudioStateUtil.checkAndJoinVoice(this)
                if(voice is AudioStateUtil.VoiceValidation.Failure) {
                    error(voice.error).awaitSingle()
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
                val track = search[0]
                FallbackHandler(this, extract = ExtractedQuery.default(track.identifier)).trackLoaded(search[0])
            }
        }
    }
}
package moe.kabii.command.commands.audio.search

import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.SelectMenu
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent
import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.command.commands.audio.AudioStateUtil
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.ExtractedQuery
import moe.kabii.discord.audio.FallbackHandler
import moe.kabii.discord.util.Embeds
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.awaitAction
import java.time.Duration

object SearchTracks : AudioCommandContainer {
    object SearchSource : Command("search") {
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                // /search <text> (youtube/soundcloud)
                channelFeatureVerify(FeatureChannel::musicChannel)
                val site = args.optInt("site")?.toInt() ?: 1
                val source = when(site) {
                    1 -> AudioSource.YOUTUBE
                    else -> AudioSource.SOUNDCLOUD
                }
                val query = args.string("search")
                event.deferReply().awaitAction()
                val search = source.handler.search(query)
                if(search.isEmpty()) {
                    event.editReply()
                        .withEmbeds(Embeds.error("No results found searching **${source.fullName}** for **$query**."))
                        .awaitSingle()
                    return@discord
                }

                // build search selection menu until 10 songs or 2000 chars
                val menu = StringBuilder()
                val options = mutableListOf<SelectMenu.Option>()
                for(index in search.indices) {
                    val id = index + 1
                    val track = search[index]
                    val author = if(track.info.author != null) " Uploaded by ${track.info.author}" else ""
                    val entry = "$id. ${trackString(track, includeAuthor = false)}$author\n"
                    if(menu.length + entry.length > MagicNumbers.Embed.NORM_DESC) break
                    menu.append(entry)

                    val option = SelectMenu.Option
                        .of(id.toString(), id.toString())
                    options.add(option)
                }
                val embed = Embeds.fbk(menu.toString())
                    .withAuthor(EmbedCreateFields.Author.of("Results from ${source.fullName} for \"$query\"", null, null))
                    .withTitle("Select tracks to be played")
                val selectMenu = SelectMenu.of("menu", options).withMaxValues(options.size)

                event.editReply()
                    .withEmbeds(embed)
                    .withComponents(ActionRow.of(selectMenu))
                    .awaitSingle()

                val response = listener(SelectMenuInteractionEvent::class, true, Duration.ofMinutes(5), "menu")
                    .switchIfEmpty {
                        event.editReply()
                            .withEmbeds(Embeds.error("No track was selected."))
                            .withComponentsOrNull(null)
                    }
                    .awaitFirstOrNull() ?: return@discord
                response.deferEdit().awaitAction()
                val selected = response.values.map(String::toInt)
                if(selected.isNotEmpty()) {
                    val voice = AudioStateUtil.checkAndJoinVoice(this)
                    if(voice is AudioStateUtil.VoiceValidation.Failure) {
                        event.editReply()
                            .withEmbeds(Embeds.error(voice.error))
                            .withComponentsOrNull(null)
                            .awaitSingle()
                        return@discord
                    }
                }
                selected.forEach { selection ->
                    val track = search[selection - 1]
                    // fallback handler = don't search or try to resolve a different track if videos is unavailable
                    FallbackHandler(this, extract = ExtractedQuery.default(track.identifier)).trackLoadedModifiers(track, silent = true, deletePlayReply = false)
                }
                event.editReply()
                    .withEmbeds(Embeds.fbk("Adding **${selected.size}** tracks to queue."))
                    .withComponentsOrNull(null)
                    .awaitSingle()
            }
        }
    }
}
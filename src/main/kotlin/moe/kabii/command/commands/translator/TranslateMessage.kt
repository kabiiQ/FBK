package moe.kabii.command.commands.translator

import discord4j.core.spec.EmbedCreateFields
import moe.kabii.command.Command
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.util.Embeds
import moe.kabii.translation.Translator
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.createJumpLink
import moe.kabii.util.extensions.orNull
import org.apache.commons.lang3.StringUtils

object TranslateMessage : Command("Translate Message") {
    override val wikiPath: String? = null // TODO

    init {
        messageInteraction {
            // form a flat representation of any contents in this discord message
            // pull contents of embeds in their client display order
            val contents = sequence {
                if (event.resolvedMessage.embeds.isEmpty()) {
                    yield(event.resolvedMessage.content)
                } else {
                    event.resolvedMessage.embeds
                        .forEach { embed ->
                            yield(embed.title.orNull())
                            yield(embed.description.orNull())
                        }
                }
            }.filterNotNull().joinToString("\n")
            if(contents.isBlank()) return@messageInteraction

            val config = event.interaction.guildId.map { id -> GuildConfigurations.getOrCreateGuild(client.clientId, id.asLong()) }?.orNull()
            val service = Translator.service
            val defaultLang = config?.translator?.defaultTargetLanguage?.run(service.supportedLanguages::get) ?: service.defaultLanguage()
            val translator = Translator.getService(contents, defaultLang.tag)
            val translation = translator.translate(from = null, to = defaultLang, text = contents)
            val jumpLink = event.resolvedMessage.createJumpLink()
            val user = event.interaction.user

            if(translation.originalLanguage != translation.targetLanguage) {

                val text = StringUtils.abbreviate(translation.translatedText, MagicNumbers.Embed.MAX_DESC)
                event.reply()
                    .withEmbeds(
                        Embeds.fbk(text)
                            .withAuthor(EmbedCreateFields.Author.of("Translation Requested for Message", jumpLink, user.avatarUrl))
                            .withFooter(EmbedCreateFields.Footer.of("Translator: ${translation.service.fullName}\nTranslation: ${translation.originalLanguage.tag} -> ${translation.targetLanguage.tag}", null))
                    )
                    .awaitAction()

            } else {

                val tag = translation.originalLanguage.tag
                event.reply()
                    .withEmbeds(
                        Embeds.error("Translation was not performed: $tag -> $tag.")
                    )
                    .withEphemeral(true)
                    .awaitAction()

            }
        }
    }
}
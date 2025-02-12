package moe.kabii.command.commands.translator

import discord4j.core.spec.EmbedCreateFields
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.TranslatorSettings
import moe.kabii.data.temporary.Cache
import moe.kabii.discord.util.Embeds
import moe.kabii.translation.Translator
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.createJumpLink
import moe.kabii.util.extensions.orNull
import org.apache.commons.lang3.StringUtils

object TranslateMessage : Command("Translate Message") {
    override val wikiPath = "Translator#-translation-commands"

    init {
        messageInteraction {
            // Interaction timing out sometimes before translation service returns, check config and defer before calling out
            val config = event.interaction.guildId.map { id -> GuildConfigurations.getOrCreateGuild(client.clientId, id.asLong()) }?.orNull()
            val ephemeral = config?.translator?.ephemeral ?: false
            event.deferReply().withEphemeral(ephemeral).awaitAction()

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
            if(contents.isBlank()) {
                val noContent = "Nothing detected for translation in this message."
                if(ephemeral) {
                    event.editReply()
                        .withEmbeds(Embeds.error(noContent))
                        .awaitSingle()
                } else {
                    event.deleteReply().awaitAction()
                    event.createFollowup()
                        .withEmbeds(
                            Embeds.error("Nothing detected for translation in this message.")
                        )
                        .withEphemeral(true)
                        .awaitSingle()
                }
                return@messageInteraction
            }

            // Cache user message translations: message ID will be unique to that server, and target language setting is currently only server-side, so no further checks are required beyond the cache
            val translation = Cache.translationCache.getOrPut(event.resolvedMessage.id) {
                val defaultLangTag = config?.translator?.defaultTargetLanguage ?: TranslatorSettings.fallbackLang
                val translator = Translator.getService(contents, listOf(defaultLangTag))
                val defaultLang = translator.getLanguage(defaultLangTag)
                translator.translate(from = null, to = defaultLang, text = contents)
            }

            if(translation.originalLanguage != translation.targetLanguage) {

                val jumpLink = event.resolvedMessage.createJumpLink()
                val user = event.interaction.user
                val text = StringUtils.abbreviate(translation.translatedText, MagicNumbers.Embed.MAX_DESC)
                event.editReply()
                    .withEmbeds(
                        Embeds.fbk(text)
                            .withAuthor(EmbedCreateFields.Author.of("Translation Requested for Message", jumpLink, user.avatarUrl))
                            .withFooter(EmbedCreateFields.Footer.of("Translator: ${translation.service.fullName}\nTranslation: ${translation.originalLanguage.tag} -> ${translation.targetLanguage.tag}", null))
                    )
                    .awaitSingle()

            } else {

                val tag = translation.originalLanguage.tag
                val noTranslation = "Translation was not performed: $tag -> $tag."
                if(ephemeral) {
                    event.editReply()
                        .withEmbeds(Embeds.error(noTranslation))
                        .awaitSingle()
                } else {
                    event.deleteReply().awaitAction()
                    event.createFollowup()
                        .withEmbeds(
                            Embeds.error("Translation was not performed: $tag -> $tag.")
                        )
                        .withEphemeral(true)
                        .awaitSingle()
                }
            }
        }
    }
}
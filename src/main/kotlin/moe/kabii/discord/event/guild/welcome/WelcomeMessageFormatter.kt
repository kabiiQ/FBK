package moe.kabii.discord.event.guild.welcome

import discord4j.core.`object`.entity.Member
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.MessageCreateFields
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.guilds.WelcomeSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.userAddress

object WelcomeMessageFormatter {

    private val nameParam = Regex("&(user|name)+", RegexOption.IGNORE_CASE)
    private val mentionParam = Regex("&mention", RegexOption.IGNORE_CASE)
    private val numberParam = Regex("&(number|count|member)", RegexOption.IGNORE_CASE)

    suspend fun format(member: Member, raw: String, rich: Boolean): String {
        var format = raw
            .replace(nameParam, member.username)
            .replace(mentionParam, if(rich) member.mention else member.userAddress())
        if(format.contains(numberParam)) {
            // check first to avoid calculating this if not needed
            val memberNumber = member.guild.awaitSingle().memberCount + 1
            format = format.replace(numberParam, memberNumber.toString())
        }
        return format
    }

    suspend fun createWelcomeMessage(config: WelcomeSettings, member: Member): MessageCreateSpec {
        // apply plain-text message in all cases
        val message = if(config.message.isBlank()) null
        else format(member, config.message, rich = true)

        val image = WelcomeImageGenerator.generate(config, member)
        val subText = if(config.includeImageText) format(member, config.imageTextValue, rich = true) else null
        return MessageCreateSpec.create()
            .run { if(message != null) withContent(message) else this }
            .run {
                // add either image (if enabled) else embed (if some rich content is enabled)
                if(image != null) {
                    withFiles(MessageCreateFields.File.of("welcome.png", image))
                } else if(config.includeUsername || config.includeAvatar || config.includeTagline || config.includeImageText) {

                    withEmbeds(
                        Embeds.other(Color.of(6750056))
                            .run { if(config.includeAvatar) withImage(member.avatarUrl) else this }
                            .run { if(config.includeUsername) withAuthor(EmbedCreateFields.Author.of(member.username, null, member.avatarUrl)) else this }
                            .run { if(config.includeTagline) withTitle(config.taglineValue) else this }
                            .run { if(subText != null) withDescription(subText) else this }
                    )
                } else this
            }
    }
}
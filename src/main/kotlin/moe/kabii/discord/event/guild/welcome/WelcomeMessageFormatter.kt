package moe.kabii.discord.event.guild.welcome

import discord4j.core.`object`.entity.Member
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.guilds.WelcomeSettings
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

    suspend fun createWelcomeMessage(config: WelcomeSettings, member: Member): MessageCreateSpec.() -> Unit {
        // apply plain-text message in all cases
        val message = if(config.message.isBlank()) null
        else format(member, config.message, rich = true)

        val image = WelcomeImageGenerator.generate(config, member)
        val subText = if(config.imageText != null) format(member, config.imageText!!, rich = true) else null
        return {
            if(message != null) this.setContent(message)

            // add either image (if enabled) else embed (if some rich content is enabled)
            if(image != null) {

                this.addFile("welcome.png", image)
            } else if(config.includeUsername || config.includeAvatar || config.welcomeTagLine != null || config.imageText != null) {

                this.setEmbed { embed ->
                    embed.setColor(Color.of(6750056))
                    if(config.includeAvatar) embed.setImage(member.avatarUrl)
                    if(config.includeUsername) embed.setAuthor(member.userAddress(), null, member.avatarUrl)
                    if(config.welcomeTagLine != null) embed.setTitle(config.welcomeTagLine)
                    subText?.run(embed::setDescription)
                }
            }
        }
    }
}
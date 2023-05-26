package moe.kabii.discord.event.guild.welcome

import discord4j.core.`object`.entity.Member
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.MessageCreateFields
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.util.Color
import moe.kabii.data.flat.GuildMemberCounts
import moe.kabii.data.mongodb.guilds.WelcomeSettings
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.tryAwait
import moe.kabii.util.extensions.userAddress
import moe.kabii.util.formatting.NumberUtil

object WelcomeMessageFormatter {

    private val nameParam = Regex("&(user|name)+", RegexOption.IGNORE_CASE)
    private val mentionParam = Regex("&mention", RegexOption.IGNORE_CASE)
    private val numberParam = Regex("&(number|count|member)(ord)?", RegexOption.IGNORE_CASE)

    suspend fun format(member: Member, raw: String, rich: Boolean): String {
        var format = raw
            .replace(nameParam, member.username)
            .replace(mentionParam, if(rich) member.mention else member.userAddress())

        val memberCountMatch = numberParam.find(format)
        if(memberCountMatch != null) {
            // check cache to avoid pulling from discord if not needed
            val memberCache = GuildMemberCounts[member.guildId.asLong()]
            val memberNumber = if(memberCache == null) {
                val memberCount = member.guild.tryAwait().orNull()?.memberCount
                if(memberCount != null) GuildMemberCounts[member.guildId.asLong()] = memberCount.toLong()
                memberCount
            } else memberCache.toInt()

            val newMember = memberNumber?.plus(1)
            val ordinal = if(newMember != null && memberCountMatch.groups[2] != null) NumberUtil.ordinalFor(newMember)
            else ""

            format = format.replace(numberParam, "${newMember ?: ""}$ordinal")
        }
        return format
    }

    suspend fun createWelcomeMessage(guildId: Long, config: WelcomeSettings, member: Member): MessageCreateSpec {
        // apply plain-text message in all cases
        val message = if(config.message.isBlank()) null
        else format(member, config.message, rich = true)

        val image = WelcomeImageGenerator.generate(guildId, config, member)
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
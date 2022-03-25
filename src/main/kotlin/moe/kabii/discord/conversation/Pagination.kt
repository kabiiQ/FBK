package moe.kabii.discord.conversation

import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.util.Embeds
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.mod

/**
 * @param pageCount - the actual number of pages
 * @param current - the page we are on 0 - (pageCount-1)
 */
data class Page(val pageCount: Int, val current: Int) {
    operator fun inc() = copy(current = (current + 1) mod pageCount)
    operator fun dec() = copy(current = (current - 1) mod pageCount)
}

object PaginationUtil {
    fun partition(pageCharLimit: Int, elements: Collection<String>): List<String> = sequence {
        var page = StringBuilder()
        elements.forEach { element ->
            if(page.length + element.length > pageCharLimit) {
                yield(page.toString())
                page = StringBuilder()
            }
            page.append(element).append('\n')
        }
        yield(page.toString().dropLast(1))
    }.toList()

    suspend fun paginateListAsDescription(origin: DiscordParameters, elements: List<String>, embedTitle: String? = null, descHeader: String? = "", detail: (EmbedCreateSpec.() -> EmbedCreateSpec)? = null) {
        val pages = partition(MagicNumbers.Embed.NORM_DESC, elements)
        var page: Page? = Page(pages.size, 0)
        var first = true

        fun pageContent(): EmbedCreateSpec {
            val thisPage = page!!
            return Embeds.fbk("$descHeader\n\n${pages[thisPage.current]}")
                .run { if(embedTitle != null) withTitle(embedTitle) else this }
                .withFooter(EmbedCreateFields.Footer.of("Page ${thisPage.current + 1}/${thisPage.pageCount}", null))
                .run { if(detail != null) detail(this) else this }
        }

        val message = origin.reply(pageContent()).awaitSingle()

        if(page!!.pageCount > 1) {
            while(page != null) {
                if(!first) {
                    message.edit()
                        .withEmbeds(pageContent())
                        .awaitSingle()
                }
                page = origin.getPage(page, message, add = first)
                first = false
            }
        }
    }
}
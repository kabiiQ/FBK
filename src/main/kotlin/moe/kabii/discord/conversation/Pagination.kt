package moe.kabii.discord.conversation

import discord4j.core.spec.EmbedCreateSpec
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.params.DiscordParameters
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

    suspend fun paginateListAsDescription(origin: DiscordParameters, elements: List<String>, embedTitle: String? = null, descHeader: String? = "", detail: ((EmbedCreateSpec) -> Unit)? = null) {
        val pages = partition(MagicNumbers.Embed.DESC, elements)
        var page: Page? = Page(pages.size, 0)
        var first = true

        fun applyPageContent(spec: EmbedCreateSpec) {
            val thisPage = page!!
            if(embedTitle != null) spec.setTitle(embedTitle)
            spec.setDescription("$descHeader\n\n${pages[thisPage.current]}")
            spec.setFooter("Page ${thisPage.current + 1}/${thisPage.pageCount}", null)
            if(detail != null) {
                detail(spec)
            }
        }

        val message = origin.embed(::applyPageContent).awaitSingle()

        if(page!!.pageCount > 1) {
            while(page != null) {
                if(!first) {
                    message.edit { spec ->
                        spec.setEmbed(::applyPageContent)
                    }.awaitSingle()
                }
                page = origin.getPage(page, message, add = first)
                first = false
            }
        }
    }
}
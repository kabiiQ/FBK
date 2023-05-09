package moe.kabii.discord.pagination

import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.util.Embeds
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.mod
import java.time.Duration

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

    suspend fun paginateListAsDescription(origin: DiscordParameters, elements: List<String>, embedTitle: String? = null, descHeader: String? = "", ephemeral: Boolean = true, detail: (EmbedCreateSpec.() -> EmbedCreateSpec)? = null) {
        val pages = partition(MagicNumbers.Embed.NORM_DESC, elements)

        fun pageContent(page: Page): EmbedCreateSpec {
            return Embeds.fbk("$descHeader\n\n${pages[page.current]}")
                .run { if(embedTitle != null) withTitle(embedTitle) else this }
                .withFooter(EmbedCreateFields.Footer.of("Page ${page.current + 1}/${page.pageCount}", null))
                .run { if(detail != null) detail(this) else this }
        }

        val buttons = ActionRow.of(
            Button.primary("prev", "<-"),
            Button.primary("next", "->")
        )

        var currPage = Page(pages.size, 0)
        if(currPage.pageCount == 1) {
            val content = pageContent(currPage)
            if(ephemeral) origin.ereply(content).awaitSingle()
            else origin.ireply(content).awaitSingle()
            return
        }
        origin.event
            .reply()
            .withEphemeral(ephemeral)
            .withEmbeds(pageContent(currPage))
            .withComponents(buttons)
            .awaitAction()

        while(true) {

            val press = origin.listener(ButtonInteractionEvent::class, false, Duration.ofMinutes(30), "prev", "next")
                .switchIfEmpty { origin.event.editReply().withComponentsOrNull(null) }
                .take(1).awaitFirstOrNull() ?: return

            currPage = when(press.customId) {
                "prev" -> currPage.dec()
                "next" -> currPage.inc()
                else -> error("component mismatch")
            }

            press.edit()
                .withEmbeds(pageContent(currPage))
                .awaitAction()
        }
    }
}
package moe.kabii.trackers.anime

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateFields
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.discord.util.Embeds
import moe.kabii.util.constants.URLUtil
import org.apache.commons.lang3.StringUtils

class MediaEmbedBuilder(val media: Media) {
    // store message details until given a discord object to build on - don't store any discord objects
    var username = ""
    var avatar = ""
    fun withUser(user: User) {
        username = user.username
        avatar = user.avatarUrl
    }

    var oldProgress: String? = null
    var oldScore: String? = null
    var descriptionFmt = ""

    fun createEmbed(site: ListSite, id: String) = Embeds.other(descriptionFmt.format("[${media.title}](${media.url})"), media.status.color)
        .withThumbnail(media.image)
        .run {
            val url = URLUtil.MediaListSite.url(site, id, media.type)
            withAuthor(EmbedCreateFields.Author.of(username, url, avatar))
        }
        .run {
            val footer = StringBuilder("Progress: ")
            if (oldProgress != null) {
                footer.append(oldProgress)
                    .append(" -> ")
            }
            footer.append(media.progressStr())
                .append(" Score: ")
            if (oldScore != null) {
                footer.append(oldScore).append(" -> ")
            }
            footer.append(media.scoreStr())
            if(media.meanScore > 0.0f) {
                val avgScore = "%.1f".format(media.meanScore)
                footer.append(" (average: $avgScore)")
            }
            if(media.notes.isNotBlank()) {
                val notes = "\n$username's notes: ${media.notes}"
                footer.append(StringUtils.abbreviate(notes, 250))
            }

            withFooter(EmbedCreateFields.Footer.of(footer.toString(), null))
        }
}
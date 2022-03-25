package moe.kabii.trackers.anime

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.legacy.LegacyEmbedCreateSpec
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.discord.util.Embeds
import moe.kabii.util.constants.URLUtil

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
            withFooter(EmbedCreateFields.Footer.of(footer.toString(), null))
        }
}
package moe.kabii.discord.trackers.anime

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import moe.kabii.data.relational.anime.ListSite
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

    fun createEmbedConsumer(site: ListSite, id: String) = fun(spec: EmbedCreateSpec) {
        val url = URLUtil.MediaListSite.url(site, id, media.type)
        spec.setColor(media.status.color)
        spec.setThumbnail(media.image)
        spec.setAuthor(username, url, avatar)

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
        spec.setFooter(footer.toString(), null)

        val title = "[${media.title}](${media.url})"
        spec.setDescription(descriptionFmt.format(title))
    }
}
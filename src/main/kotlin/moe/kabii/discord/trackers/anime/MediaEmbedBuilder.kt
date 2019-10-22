package moe.kabii.discord.trackers.anime

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import moe.kabii.data.mongodb.ListInfo
import moe.kabii.data.mongodb.MediaSite

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

    fun createEmbedConsumer(listInfo: ListInfo) = fun(spec: EmbedCreateSpec) {
        val id = listInfo.id
        val url = when(listInfo.site) {
            MediaSite.MAL -> when(media.type) {
                MediaType.ANIME -> "https://myanimelist.net/animelist/$id"
                MediaType.MANGA -> "https://myanimelist.net/mangalist/$id"
            }
            MediaSite.KITSU -> when(media.type) {
                MediaType.ANIME -> "https://kitsu.io/users/$id/library?media=anime"
                MediaType.MANGA -> "https://kitsu.io/users/$id/library?media=manga"
            }
        }

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
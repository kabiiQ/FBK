package moe.kabii.trackers.anime

import discord4j.core.`object`.entity.User
<<<<<<< HEAD:src/main/kotlin/moe/kabii/trackers/anime/MediaEmbedBuilder.kt
import discord4j.core.spec.EmbedCreateFields
=======
import discord4j.core.spec.legacy.LegacyEmbedCreateSpec
>>>>>>> master:src/main/kotlin/moe/kabii/discord/trackers/anime/MediaEmbedBuilder.kt
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

<<<<<<< HEAD:src/main/kotlin/moe/kabii/trackers/anime/MediaEmbedBuilder.kt
    fun createEmbed(site: ListSite, id: String) = Embeds.other(descriptionFmt.format("[${media.title}](${media.url})"), media.status.color)
        .withThumbnail(media.image)
        .run {
            val url = URLUtil.MediaListSite.url(site, id, media.type)
            withAuthor(EmbedCreateFields.Author.of(username, url, avatar))
=======
    fun createEmbedConsumer(site: ListSite, id: String) = fun(spec:LegacyEmbedCreateSpec) {
        val url = URLUtil.MediaListSite.url(site, id, media.type)
        spec.setColor(media.status.color)
        spec.setThumbnail(media.image)
        spec.setAuthor(username, url, avatar)

        val footer = StringBuilder("Progress: ")
        if (oldProgress != null) {
            footer.append(oldProgress)
                    .append(" -> ")
>>>>>>> master:src/main/kotlin/moe/kabii/discord/trackers/anime/MediaEmbedBuilder.kt
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
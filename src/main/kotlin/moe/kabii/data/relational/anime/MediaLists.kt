package moe.kabii.data.relational.anime

import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.discord.trackers.AniListTarget
import moe.kabii.discord.trackers.AnimeTarget
import moe.kabii.discord.trackers.KitsuTarget
import moe.kabii.discord.trackers.MALTarget
import moe.kabii.discord.trackers.anime.MediaListParser
import moe.kabii.discord.trackers.anime.anilist.AniListParser
import moe.kabii.discord.trackers.anime.kitsu.KitsuParser
import moe.kabii.discord.trackers.anime.mal.MALParser
import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.LowerCase
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

enum class ListSite(val targetType: AnimeTarget, val parser: MediaListParser) {
    MAL(MALTarget, MALParser),
    KITSU(KitsuTarget, KitsuParser),
    ANILIST(AniListTarget, AniListParser)
}

object TrackedMediaLists {
    object MediaLists : IntIdTable() {
        val site = enumeration("site_id", ListSite::class)
        val siteChannelId = varchar("site_channel_id", 64).uniqueIndex()
        val lastListJson = text("last_list_json")
    }

    class MediaList(id: EntityID<Int>) : IntEntity(id) {
        var site by MediaLists.site
        var siteListId by MediaLists.siteChannelId
        var lastListJson by MediaLists.lastListJson

        val targets by ListTarget referrersOn ListTargets.mediaList

        suspend fun downloadCurrentList() = site.parser.parse(siteListId)

        fun extractMedia(): List<DBMedia> = DBMediaList.jsonAdapter.fromJson(this.lastListJson)!!.items

        companion object : IntEntityClass<MediaList>(MediaLists)
    }

    object ListTargets : IntIdTable() {
        val mediaList = reference("assoc_media_list", MediaLists, ReferenceOption.CASCADE)
        val discord = reference("target_discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
        val userTracked = reference("discord_user_tracked", DiscordObjects.Users, ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(mediaList, discord)
    }

    class ListTarget(id: EntityID<Int>) : IntEntity(id) {
        var mediaList by MediaList referencedOn ListTargets.mediaList
        var discord by DiscordObjects.Channel referencedOn ListTargets.discord
        var userTracked by DiscordObjects.User referencedOn ListTargets.userTracked

        companion object : IntEntityClass<ListTarget>(ListTargets) {
            @WithinExposedContext
            fun getExistingTarget(site: ListSite, listIdLower: String, channelId: Long) = ListTarget.wrapRows(
                ListTargets
                    .innerJoin(MediaLists)
                    .innerJoin(DiscordObjects.Channels)
                    .select {
                        MediaLists.site eq site and
                                (LowerCase(MediaLists.siteChannelId) eq listIdLower) and
                                (DiscordObjects.Channels.channelID eq channelId)
                    }
            ).firstOrNull()
        }
    }
}
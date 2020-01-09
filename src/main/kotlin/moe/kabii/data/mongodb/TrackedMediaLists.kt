package moe.kabii.data.mongodb

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import moe.kabii.discord.trackers.anime.*
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.updateOne
import org.litote.kmongo.newId

object TrackedMediaLists {
    // global - animelist collection
    val mediaLists: MutableList<TrackedMediaList>
    val mongoMediaLists = MongoDBConnection.mongoDB.getCollection<TrackedMediaList>()
    val mutex = Mutex()

    init {
        mediaLists = runBlocking {
            mongoMediaLists.find().toList().toMutableList()
        }
    }
    suspend fun remove(list: TrackedMediaList) {
        mediaLists.remove(list)
        mongoMediaLists.deleteOneById(list._id)
    }
}

data class TrackedMediaList(
    val _id: Id<TrackedMediaList> = newId(),
    val list: ListInfo,
    val targets: MutableSet<MediaTarget> = mutableSetOf(),
    var savedMediaList: MediaList) {
    suspend fun save() = TrackedMediaLists.mongoMediaLists.updateOne(this@TrackedMediaList,
        upsert
    )
    suspend fun removeSelf() {
        TrackedMediaLists.remove(this)
        TrackedMediaLists.mongoMediaLists.deleteOneById(this._id)
    }
}

data class MediaTarget(
        val channelID: Long,
        val discordUserID: Long
)

data class ListInfo(
    val site: MediaSite,
    val id: String)

enum class MediaSite(val full: String, val parser: MediaListParser) {
    MAL("MyAnimeList", MALParser),
    KITSU("Kitsu", KitsuParser)
}
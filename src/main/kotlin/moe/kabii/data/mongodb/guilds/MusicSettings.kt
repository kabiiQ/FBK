package moe.kabii.data.mongodb.guilds

data class MusicSettings(
    var startingVolume: Long = defaultStartingVolume.toLong(),
    var lastChannel: Long? = null,
    var sendNowPlaying: Boolean = true,
    var deleteNowPlaying: Boolean = true,
    var queuerFSkip: Boolean = true,
    var restrictFilters: Boolean = false,
    var restrictSeek: Boolean = true,
    var autoFSkip: Boolean = true,
    var skipIfAbsent: Boolean = false,
    var skipRatio: Long = defaultRatio,
    var skipUsers: Long = defaultUsers,
    var maxTracksUser: Long = defaultMaxTracksUser,
    var volumeLimit: Long = defaultVolumeLimit,
    var activeQueue: List<QueuedTrack> = listOf()
) {
    companion object {
        const val defaultRatio = 50L
        const val defaultUsers = 4L
        const val defaultStartingVolume = 15
        const val defaultMaxTracksUser = 0L
        const val defaultVolumeLimit = 100L
    }

    // just serializable info that we need to requeue the tracks after a restart
    data class QueuedTrack(
        val uri: String,
        val author_name: String,
        val author: Long,
        val originChannel: Long
    )
}
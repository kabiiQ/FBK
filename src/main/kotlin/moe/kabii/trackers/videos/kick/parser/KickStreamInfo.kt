package moe.kabii.trackers.videos.kick.parser

import moe.kabii.trackers.videos.kick.json.KickCategory
import moe.kabii.util.constants.URLUtil
import java.time.Instant

data class KickStreamInfo(
    val userId: Long,
    val slug: String,
    val title: String,
    val category: KickCategory,
    val live: Boolean,
    val startTime: Instant,
    val viewers: Int
) {
    val url = URLUtil.StreamingSites.Kick.channelByName(slug)
}
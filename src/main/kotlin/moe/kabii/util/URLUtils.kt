package moe.kabii.util

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.util.Snowflake
import moe.kabii.structure.tryBlock

object YoutubeUtil {
    fun thumbnailUrl(videoID: String) = "https://i.ytimg.com/vi/$videoID/maxresdefault.jpg"
}
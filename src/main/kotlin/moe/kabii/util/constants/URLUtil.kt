package moe.kabii.util.constants

import moe.kabii.data.relational.anime.ListSite
import moe.kabii.trackers.anime.MediaType

object URLUtil {
    const val colorPicker = "https://htmlcolorcodes.com/color-picker/"

    val genericUrl = Regex(
        "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))",
        RegexOption.IGNORE_CASE
    )

    object StreamingSites {
        object Youtube {
            fun thumbnail(videoID: String) = "https://i.ytimg.com/vi/$videoID/maxresdefault.jpg"
            fun channel(id: String) = "https://youtube.com/channel/$id"
            fun video(id: String) = "https://youtube.com/watch?v=$id"
        }

        object Twitch {
            fun channelByName(name: String) = "https://twitch.tv/$name"
        }

        object TwitCasting {
            fun channelByName(name: String) = "https://twitcasting.tv/$name"
        }
    }

    object MediaListSite {
        fun url(site: ListSite, id: String, mediaType: MediaType = MediaType.ANIME) = when(site) {
            ListSite.MAL -> Mal.list(id, mediaType)
            ListSite.KITSU -> Kitsu.list(id, mediaType)
            ListSite.ANILIST -> Anilist.list(id, mediaType)
        }

        object Kitsu {
            fun list(id: String, type: MediaType) = when(type) {
                MediaType.ANIME -> "https://kitsu.io/users/$id/library?media=anime"
                MediaType.MANGA -> "https://kitsu.io/users/$id/library?media=manga"
            }
        }
        object Mal {
            fun list(id: String, type: MediaType) = when(type) {
                MediaType.ANIME -> "https://myanimelist.net/animelist/$id"
                MediaType.MANGA -> "https://myanimelist.net/mangalist/$id"
            }
        }
        object Anilist {
            fun list(id: String, type: MediaType) = when(type) {
                MediaType.ANIME -> "https://anilist.co/user/$id/animelist"
                MediaType.MANGA -> "https://anilist.co/user/$id/mangalist"
            }
        }
    }

    object Twitter {
        fun feed(id: String) = "https://twitter.com/i/user/$id"
        fun feedUsername(username: String) = "https://twitter.com/$username"
        fun tweet(id: String) = "https://twitter.com/FBK/status/$id"
        fun space(id: String) = "https://twitter.com/i/spaces/$id"
    }
}
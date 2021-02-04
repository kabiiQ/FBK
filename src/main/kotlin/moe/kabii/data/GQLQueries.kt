package moe.kabii.data

import java.io.File

object GQLQueries {
    val aniListUser: String = File("anilist/user.gql").readText()
    val aniListMediaList: String = File("anilist/medialist.gql").readText()
}
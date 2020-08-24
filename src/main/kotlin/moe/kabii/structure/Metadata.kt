package moe.kabii.structure

import discord4j.common.util.Snowflake
import discord4j.common.util.TokenUtil
import moe.kabii.data.Keys
import moe.kabii.structure.extensions.snowflake
import java.time.Instant

class Metadata private constructor(
    val major: Int,
    val minor: Int,
    val build: Int,
    val flag: String?) {

    companion object {
        val current: Metadata? = this::class.java.classLoader.getResourceAsStream("build.version")?.run {
            val (major, minor, build, flag) = bufferedReader().readLines().map { line -> line.substring(line.indexOf(' ') + 1, line.length) }
            Metadata(
                major = major.toInt(),
                minor = minor.toInt(),
                build = build.toInt(),
                flag = if(flag.isBlank()) null else flag
            )
        }

        val buildInfo: String = if(current == null) "Development Build" else {
            val currentFlag = if(current.flag.isNullOrBlank()) "-${current.flag}" else ""
            "Release ${current.major}.${current.minor}$currentFlag\nbuild #${current.build}"
        }
    }
}

object Uptime {
    val connection: Instant = Instant.now()

    var reconnect: Instant = Instant.now()
    private set

    fun update() {
        reconnect = Instant.now()
    }
}

object DiscordBot {
    val selfId: Snowflake = TokenUtil.getSelfId(Keys.config[Keys.Discord.token]).snowflake
}

object SourcePaths {
    const val gitURL = "https://github.com/kabiiQ/FBK"
    val sourceRoot = "$gitURL/tree/master/src/main/kotlin"
    val wikiURL = "$gitURL/wiki"
}
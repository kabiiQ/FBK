package moe.kabii.discord.util

import moe.kabii.data.flat.Keys
import java.time.Instant

class MetaData private constructor(
    val major: Int,
    val minor: Int,
    val build: Int,
    val flag: String?) {

    companion object {
        val current: MetaData? = this::class.java.classLoader.getResourceAsStream("build.version")?.run {
            val (major, minor, build, flag) = bufferedReader().readLines().map { line -> line.substring(line.indexOf(' ') + 1, line.length) }
            MetaData(
                major = major.toInt(),
                minor = minor.toInt(),
                build = build.toInt(),
                flag = if(flag.isBlank()) null else flag
            )
        }

        val buildInfo: String = if(current == null) "Development Build" else {
            val currentFlag = if(!current.flag.isNullOrBlank()) "-${current.flag}" else ""
            "Release ${current.major}.${current.minor}$currentFlag build #${current.build}"
        }

        val host = Keys.config[Keys.Dev.host]
    }
}

class Uptime {
    val connection: Instant = Instant.now()

    var reconnect: Instant = Instant.now()
    private set

    fun update() {
        reconnect = Instant.now()
    }
}

object SourcePaths {
    const val gitURL = "https://github.com/kabiiQ/FBK"
    val sourceRoot = "$gitURL/tree/master/src/main/kotlin"
    val wikiURL = "$gitURL/wiki"
}
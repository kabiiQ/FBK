package moe.kabii.structure

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
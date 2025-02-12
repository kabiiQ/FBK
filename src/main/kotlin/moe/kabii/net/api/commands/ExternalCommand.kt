package moe.kabii.net.api.commands

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import moe.kabii.command.commands.audio.TrackPlay
import moe.kabii.command.commands.audio.TrackSkip

typealias SnowflakeString = String

sealed class ExternalCommand(
    @Json(name = ".class") val command: String = "",
    val userId: SnowflakeString,
    val channelId: SnowflakeString
) {
    class Play(
        userId: SnowflakeString,
        channelId: SnowflakeString,
        val query: String,
        val voiceChannelId: SnowflakeString,
        val force: Boolean = false
    ) : ExternalCommand(userId = userId, channelId = channelId)

    class Skip(userId: SnowflakeString, channelId: SnowflakeString) : ExternalCommand(userId = userId, channelId = channelId)

    companion object {
        private val moshi = Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(ExternalCommand::class.java, ".class")
                    .withSubtype(Play::class.java, "fbk.music.play")
                    .withSubtype(Skip::class.java, "fbk.music.skip")
            )
            .add(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(ExternalCommand::class.java)
    }

    fun executable() = when(this) {
        is Play -> TrackPlay.PlaySong
        is Skip -> TrackSkip.SkipCommand
    }
}
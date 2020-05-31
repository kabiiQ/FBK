package moe.kabii.discord.trackers.streams

import moe.kabii.data.relational.TrackedStreams
import moe.kabii.rusty.Result
import discord4j.rest.util.Color

sealed class StreamErr {
    object NotFound : StreamErr()
    object IO : StreamErr()
}

interface StreamParser {
    val site: TrackedStreams.Site
    val color: Color
    val icon: String

    fun getUser(id: Long): Result<StreamUser, StreamErr>
    fun getUsers(ids: Collection<Long>): Map<Long, Result<StreamUser, StreamErr>>
    fun getUser(name: String): Result<StreamUser, StreamErr>
    fun getStream(id: Long): Result<StreamDescriptor, StreamErr>
    fun getStreams(ids: Collection<Long>): Map<Long, Result<StreamDescriptor, StreamErr>>
}
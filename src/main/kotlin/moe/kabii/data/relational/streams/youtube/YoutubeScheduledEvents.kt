package moe.kabii.data.relational.streams.youtube

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.jodatime.datetime

object YoutubeScheduledEvents : LongIdTable() {
    val ytVideo = reference("yt_video", YoutubeVideos, ReferenceOption.CASCADE).uniqueIndex()
    val scheduledStart = datetime("scheduled_start_time")
    val dataExpiration = datetime("data_expiration_time")
}

class YoutubeScheduledEvent(id: EntityID<Long>) : LongEntity(id) {
    var ytVideo by YoutubeVideo referencedOn YoutubeScheduledEvents.ytVideo
    var scheduledStart by YoutubeScheduledEvents.scheduledStart
    var dataExpiration by YoutubeScheduledEvents.dataExpiration

    companion object : LongEntityClass<YoutubeScheduledEvent>(YoutubeScheduledEvents)
}
package moe.kabii.util.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply

class ExposedFilter : Filter<ILoggingEvent>() {

    override fun decide(event: ILoggingEvent): FilterReply = if(event.loggerName == "Exposed" && event.level == Level.WARN && event.message.contains("Entity instance in cache differs from the provided")) FilterReply.DENY else FilterReply.NEUTRAL
}
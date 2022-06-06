package moe.kabii.data.flat

object GuildMemberCounts {
    private val cache: MutableMap<Long, Long> = mutableMapOf()

    operator fun get(guildId: Long) = cache[guildId]
    operator fun set(guildId: Long, memberCount: Long) {
        cache[guildId] = memberCount
    }
}
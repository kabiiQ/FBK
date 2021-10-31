package moe.kabii.net.api.videos

data class VideoRequestFilters(
    var includeUpcoming: Boolean,
    var includeLive: Boolean,
    var includePast: Boolean,
) {
    companion object {
        fun includeAll() = VideoRequestFilters(
            includeUpcoming = true,
            includeLive = true,
            includePast = true
        )
        fun filterAll() = VideoRequestFilters(
            includeUpcoming = false,
            includeLive = false,
            includePast = false
        )
    }
    fun includeChats() {
        includeUpcoming = true
        includeLive = true
        includePast = false
    }
    fun valid() = booleanArrayOf(includeUpcoming, includeLive, includePast).any(true::equals)
}

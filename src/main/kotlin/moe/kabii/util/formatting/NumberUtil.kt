package moe.kabii.util.formatting

object NumberUtil {
    fun getRanges(values: Collection<Int>): List<IntRange> {
        // sort, conseq, increase range, else new
        var head: Int? = null
        val list = values.sorted()
        val ranges = mutableListOf<IntRange>()
        for(index in list.indices) {
            val value = list[index]
            if(head == null) { // start new range
                head = value
            }
            // if next value is not consecutive, add current range to list
            val next = list.getOrNull(index + 1)
            if(next != value + 1) {
                ranges.add(head..value)
                head = null
            }
        }
        return ranges
    }
}
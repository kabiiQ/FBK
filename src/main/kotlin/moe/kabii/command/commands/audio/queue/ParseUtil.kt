package moe.kabii.command.commands.audio.queue

object ParseUtil {
    data class RangeParse(val selected: List<Int>, val invalid: List<String>)

    fun parseRanges(maxSelection: Int, args: List<String>?): RangeParse {
        // accept multiple track selection, used in remove/search commands
        // accept single tracks as normal (3), multiple tracks (3 5 9) ranges (3-6), and selectAll (all/empty range "-")
        val invalidArg = mutableListOf<String>()
        val ranges = sequence {
            // "all" / "-"
            if(args == null || args[0].lowercase() == "all" || args[0].lowercase() == "-") {
                yield(1..maxSelection)
                return@sequence
            }
            args.map { arg ->
                val parts = arg.split("-")
                when(parts.size) {
                    1 -> { // can only be a single track selection
                        val track = parts[0].toIntOrNull()
                        if(track != null && track in 1..maxSelection) yield(track..track)
                        else invalidArg.add(arg)
                        return@map
                    }
                    2 -> { // range ex. 3-5
                        val lower = (if(parts[0].isEmpty()) null else parts[0].toIntOrNull()) ?: 1
                        val upper = (if(parts[1].isEmpty()) null else parts[1].toIntOrNull()) ?: maxSelection
                        if(lower > upper || lower < 1 || upper > maxSelection) {
                            invalidArg.add(arg)
                            return@map
                        }
                        yield(lower..upper)
                    }
                    else -> {
                        invalidArg.add(arg)
                        return@map
                    }
                }
            }
        }
        val selected = ranges.toList().flatten().sorted().distinct()
        return RangeParse(selected, invalidArg)
    }
}
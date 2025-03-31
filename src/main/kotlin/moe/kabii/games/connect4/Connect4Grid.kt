package moe.kabii.games.connect4

data class GridCoordinate(val colIndex: Int, val rowIndex: Int)

class Connect4Grid {
    companion object {
        const val width = 7
        const val height = 6
        const val win = 4

        // ":one::two::three::four::five::six::seven:"
        val rowHeader: String = (1..width).joinToString("") { int -> "$int⃣" }
    }

    // private final CircleState[height][width] grid + fill with CircleState.NONE
    private val grid = Array(width) { Array(height) { CircleState.NONE } }

    fun drawGrid(): String {
        // converting from [x][y] grid to single 'string' which must be derived by LINE or each 'row' - not by natural 'columns' of grid
        // append emotes for each 'x' value onto the 'y' strings
        val rows = Array(height) { StringBuilder() }

        grid.forEachIndexed { _, colContent ->
            colContent.forEachIndexed { rowIndex, circleState ->
                rows[rowIndex].append(circleState.emote)
            }
        }

        val gameRows = rows
            .map(StringBuilder::toString)
            .reversed()
            .toTypedArray()
        return arrayOf(rowHeader, *gameRows)
            .joinToString("\n")
    }

    fun validateDrop(colNum: Int): GridCoordinate? {
        // subtract 1 to get index so that we can just drop(1) rather than drop(0)
        val colIndex = colNum - 1

        // drop in the lowest available y in column colNum
        val target = grid[colIndex].indexOfFirst(CircleState.NONE::equals)
        return if(target == -1) null else GridCoordinate(colIndex, target)
    }

    fun applyCircle(coord: GridCoordinate, state: CircleState) {
        grid[coord.colIndex][coord.rowIndex] = state
    }

    fun checkForWinFromCircle(circle: GridCoordinate): List<GridCoordinate> {
        // generate possible winning lines from this circle placement

        // horizontal check ----
        // check all 'x' columns for static 'y'/row
        val horizontal = (0 until width).map { colIndex -> GridCoordinate(colIndex, circle.rowIndex) }

        // vertical check |
        // check all 'y' rows for static 'x'/column
        val vertical = (0 until height).map { rowIndex -> GridCoordinate(circle.colIndex, rowIndex) }

        val y = circle.rowIndex
        val x = circle.colIndex

        // diagonal check /
        val diagonalUp = sequence {
            var (posX, posY) = if (y > x) {
                // y=mx+b m=1 y=x+b
                // above y=x (b>0), start at left column (x = 0), y-x finds placement in left column
                GridCoordinate(0, y - x)
            } else {
                // below y=x, start at bottom row, x-y finds placement in bottom row
                GridCoordinate(x - y, 0)
            }

            do {
                yield(GridCoordinate(posX, posY))
            } while(++posX < width && ++posY < height)
        }.toList()

        // diagonal check \
        val diagonalDown = sequence {
            val top = height - 1
            var (posX, posY) = if (x + y > top) {
                // above center, find column placement and start in top row
                val startX = (y + x) - top
                GridCoordinate(startX, top)
            } else {
                // below center, start in left column and find row placement
                val startY = y + x
                GridCoordinate(0, startY)
            }

            do {
                yield(GridCoordinate(posX, posY))
            } while(++posX < width && --posY >= 0)
        }.toList()

        val winConditions = listOf(horizontal, vertical, diagonalUp, diagonalDown)
        return winConditions.flatMap(::findWins)
    }

    private fun findWins(line: List<GridCoordinate>): List<GridCoordinate> {
        var sequenceColor = CircleState.NONE
        var sequenceStart = 0
        line.forEachIndexed { index, coord ->
            val (x, y) = coord
            val circle = grid[x][y]
            if (circle == sequenceColor && sequenceColor != CircleState.NONE) {
                val chain = index - sequenceStart + 1
                if (chain >= win) {
                    // allow the user to connect more than 4 as a power move
                    if(index < line.lastIndex) {
                        val (nextX, nextY) = line[index + 1]
                        val nextCircle = grid[nextX][nextY]
                        if(nextCircle == sequenceColor) return@forEachIndexed // continue
                    }
                    // if this is last index or next is not same color, then their chain is complete
                    return line.slice(sequenceStart..index)
                }
            } else {
                sequenceStart = index
                sequenceColor = circle
            }
        }
        return emptyList()
    }
}
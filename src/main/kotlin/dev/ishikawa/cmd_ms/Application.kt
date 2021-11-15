package dev.ishikawa.cmd_ms

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.util.logging.ConsoleHandler
import java.util.logging.Logger
import java.util.logging.Level
import java.util.logging.SimpleFormatter
import kotlin.random.Random
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    // TODO: handle interruption

    val gameSetting = GameSetting.fromArgs(args)
    val game = Game(gameSetting)

    game.start()
}


data class GameSetting(
    val density: Float,
    val size: Int,
    val playerName: String,
    val isDebug: Boolean
) {
    companion object {
        fun fromArgs(args: Array<String>): GameSetting {
            val parser = ArgParser("hoge")
            val size by parser.option(ArgType.Int, shortName = "s", description = "size of the map").default(10)
            val density by parser.option(ArgType.Double, shortName = "d", description = "density of mines").default(0.3)
            val playerName by parser.option(ArgType.String, shortName = "pn", description = "Your player name").default("anonymous")
            val isDebug by parser.option(ArgType.Boolean, shortName = "id", description = "use fixed map for debugging").default(false)

            parser.parse(args)

            assert(size in (1..100))
            assert(density in (0.0..1.0))

            return GameSetting(size = size, density = density.toFloat(), playerName = playerName, isDebug = isDebug)
        }
    }
}


class Cell(
    private var isOpen: Boolean = false,
    private var isFlagged: Boolean = false,
    val hasMine: Boolean = false,
) {
    val hasOpened: Boolean
        get() = isOpen

    val hasBeenFlagged: Boolean
        get() = isFlagged

    /**
     * open checks its isOpen and set it true when it's not yet opened, and then return true.
     * otherwise returns false
     * */
    fun tryOpen(): CellOpenResult {
        if(isOpen) return CellOpenResult.ALREADY_OPENED
        isOpen = true

        if(hasMine) return CellOpenResult.EXPLODED

        return CellOpenResult.SUCCESS
    }

    fun toggleFlag(): CellFlagResult {
        if(isOpen) return CellFlagResult.ALREADY_OPENED
        isFlagged = !isFlagged
        return CellFlagResult.SUCCESS
    }

    fun status(): CalculatedStatus = when {
        hasOpened && hasMine -> CalculatedStatus.EXPLODED
        hasOpened && !hasMine -> CalculatedStatus.OPENED
        !hasOpened && hasBeenFlagged -> CalculatedStatus.FLAGGED
        !hasOpened && !hasBeenFlagged -> CalculatedStatus.NOT_FLAGGED
        else -> throw RuntimeException("reached unexpected line. A cell has invalid status. Please report this issue to the game creator")
    }

    enum class CalculatedStatus {
        OPENED,
        EXPLODED,
        FLAGGED,
        NOT_FLAGGED
    }

    enum class CellOpenResult {
        SUCCESS,
        EXPLODED,
        ALREADY_OPENED
    }

    enum class CellFlagResult {
        SUCCESS,
        ALREADY_OPENED
    }
}

class Map(
    private val gameSetting: GameSetting
) {
    private val map: List<List<Cell>>
    private val random = if (gameSetting.isDebug) Random(0) else Random.Default

    init {
        map = initMap()
    }

    /**
     * info returns information for generating showMap
     * O(N = N*8). This has space to improve within the same order class
     * */
    fun info(): List<List<Pair<Cell.CalculatedStatus, Int>>> {
        return map.mapIndexed { i, row ->
            row.mapIndexed { j, cell ->
                if(cell.hasOpened) {
                    Pair(cell.status(), calcNumOfMinesAround(i, j))
                } else {
                    Pair(cell.status(), -1)
                }
            }
        }
    }

    fun tryOpen(i: Int, j: Int): Cell.CellOpenResult {
        val result = map[i][j].tryOpen()

        if(result == Cell.CellOpenResult.SUCCESS) {
            openNeighbors(i, j)
        }

        return result
    }

    fun tryFlag(i: Int, j: Int): Cell.CellFlagResult {
        return map[i][j].toggleFlag()
    }

    fun cleared(): Boolean {
        map.forEach { row ->
            row.forEach { cell ->
                if(cell.hasMine && !cell.hasBeenFlagged) return false
            }
        }

        return true
    }

    /**
     * openNeighbors opens neighboars that can be opened.
     * O(N)
     * */
    private fun openNeighbors(i: Int, j: Int) {
        val numOfMinesAround = calcNumOfMinesAround(i, j)
        if(numOfMinesAround > 0) return

        listOf(
            Pair(i-1, j-1),
            Pair(i-1, j),
            Pair(i-1, j+1),
            Pair(i, j-1),
            Pair(i, j+1),
            Pair(i+1, j-1),
            Pair(i+1, j),
            Pair(i+1, j+1)
        ).forEach { neighborPos ->
            val nextI = neighborPos.first
            val nextJ = neighborPos.second
            if(nextI < 0 || nextI >= gameSetting.size || nextJ < 0 || nextJ >= gameSetting.size) {
                return@forEach
            }

            val neighborCell = map[nextI][nextJ]
            if(neighborCell.hasOpened) return@forEach

            // there should be no mines around current cell.
            neighborCell.tryOpen()
            openNeighbors(nextI, nextJ)
        }
    }

    private fun calcNumOfMinesAround(i: Int, j: Int): Int {
        return (maxOf(i - 1, 0)..minOf(i + 1, gameSetting.size-1)).toList().map { x ->
            (maxOf(j - 1, 0)..minOf(j + 1, gameSetting.size-1)).toList().map { y ->
                if ((x == i && y == j) || !map[x][y].hasMine) {
                    0
                } else {
                    1
                }
            }
        }.flatten().sum()
    }

    /**
     * create a map size of which is `size` * `size`, and set
     * */
    private fun initMap(): List<List<Cell>> {
        var noMine = true
        var map: List<List<Cell>> = emptyList()
        while(noMine) {
            map = (0 until gameSetting.size).map {
                (0 until gameSetting.size).map {
                    Cell(hasMine = shouldHaveMine())
                }
            }
            noMine = map.all { row -> row.all { cell -> !cell.hasMine } }
        }

        return map
    }

    /**
     * shouldHaveMine decides whether a cell have mine or not
     * */
    private fun shouldHaveMine(): Boolean {
        return random.nextFloat() < gameSetting.density
    }

    companion object {
        private val random = Random.Default
        private val fixedRandom = Random(0)
    }
}


class Game(
    private val setting: GameSetting,
    private val map: Map = Map(setting)
) {
    private var status: Status = Status.PLAYING
    private var numOfTurns: Int = 0

    fun start() {
        console("""
Let's start mine sweeper on your console!
Give flags to all the cells that have mines
        """.trimIndent())

        mainLoop()
    }

    private fun mainLoop() {
        while(true) {
            console("> Select your command: open(o) / flag(f) / showMap(s) / giveup(g) / help(h)")

            val cmdWithPosition = readCommand()
            val cmd = cmdWithPosition.first
            val pos = cmdWithPosition.second

            when(cmd) {
                Command.OPEN -> {
                    if(tryOpen(pos)) numOfTurns++
                }
                Command.FLAG -> {
                    if(flag(pos)) numOfTurns++
                }
                Command.SHOWMAP -> {
                    showMap()
                    continue
                }
                Command.HELP -> {
                    showHelp()
                    continue
                }
                Command.INVALID -> {
                    println("your input is invalid.")
                    continue
                }

                Command.GIVEUP -> giveUp()
            }

            endTurn()
        }
    }

    private fun readCommand(): Pair<Command, Pair<Int, Int>?> {
        return readLine()
            ?.let { line ->
                val result = commandWithPositionPattern.find(line.trim())?.groups as? MatchNamedGroupCollection ?: return@let null

                val cmd = result["command"]?.value
                    ?.let { rawCmd -> Command.values()
                        .find { cmd ->
                            // short-hand command is allowed
                            cmd.name.lowercase() == rawCmd.lowercase() ||
                                    cmd.name.lowercase().first() == rawCmd.lowercase().first()
                        }
                    }
                    ?: return@let null

                val pos = result["position"]?.value
                    ?.let { Pair(result["left"]!!.value.toInt(), result["right"]!!.value.toInt()) }

                Pair(cmd, pos)
            }
            ?: Pair(Command.INVALID, null)
    }

    // TODO: parse error handling
    private fun readPosition(): Pair<Int, Int> {
        var pos = _readPosition()
        while(pos == null) {
            console("> Input postion of the cell to open again, please.")
            pos = readPosition()
        }
        return pos
    }

    private fun _readPosition(): Pair<Int, Int>? {
        return readLine()
            ?.let {
                it.trim()
                    .also { input ->
                        if(!input.matches(regex = positionPattern)) {
                            console("Your specifying position($it) doesn't match position pattern. expected pattern is $positionPattern")
                            return@let null
                        }
                    }
                    .split(" ").map { it.toInt() }
                    .also { positionIdx ->
                        if(positionIdx[0] < 0 || positionIdx[0] >= setting.size || positionIdx[1] < 0 || positionIdx[1] >= setting.size) {
                            console("Your specifying position($it) is not within the map. available position is 0 to $setting.size")
                            return@let null
                        }
                    }.let { rawPos -> Pair(rawPos[0], rawPos[1]) }
            }
    }


    /**
     * @return opened a cell successfully or not
     * */
    private fun tryOpen(givenPosition: Pair<Int, Int>?): Boolean {
        val pos = if(givenPosition != null) {
            givenPosition
        } else {
            console("> which cell do you open? Input x-position and y-position in 0-index way. ex: 1 2")
            readPosition()
        }

        val result = map.tryOpen(pos.first, pos.second)
        when(result){
            Cell.CellOpenResult.SUCCESS -> println("opened successfully!")
            Cell.CellOpenResult.EXPLODED -> exploded()
            // TODO: これはturnをincrしない
            Cell.CellOpenResult.ALREADY_OPENED -> println("Your specifying position $pos has been already opened")
        }

        showMap()

        return result == Cell.CellOpenResult.SUCCESS
    }

    /**
     * @return flagged a cell successfully or not
     * */
    private fun flag(givenPosition: Pair<Int, Int>?): Boolean {
        val pos = if(givenPosition != null) {
            givenPosition
        } else {
            console("> which cell do you flag/unflag? Input x-position and y-position in 0-index way. ex: 1 2")
            readPosition()
        }

        val result = map.tryFlag(pos.first, pos.second)
        when(result) {
            Cell.CellFlagResult.SUCCESS -> println("toggle flag on the cell $pos")
            Cell.CellFlagResult.ALREADY_OPENED -> println("your specifying position $pos has been already opened, so it cannot be flagged")
        }

        showMap()
        return result == Cell.CellFlagResult.SUCCESS
    }

    private fun showMap() {
        val mapView = buildMapView()
        console("current turn is: $numOfTurns")
        console("current map is:\n$mapView")
    }

    // TODO: viewerに移す
    private fun buildMapView(): String {
        val header = "     " + (0 until setting.size).joinToString("") { it.toString().padStart(4) } + "\n" +
                "     " + ("----".repeat(setting.size))
        val body = map.info().mapIndexed { i, row ->
            "${i.toString().padStart(3)} | " + row.mapIndexed { j, cellInfo ->
                val status = cellInfo.first
                val num = cellInfo.second
                when(status) {
                    Cell.CalculatedStatus.FLAGGED -> "F"
                    Cell.CalculatedStatus.NOT_FLAGGED -> "-"
                    Cell.CalculatedStatus.EXPLODED -> "💣"
                    Cell.CalculatedStatus.OPENED -> if(num == 0) " " else num.toString()
                }.padStart(3)
            }.joinToString(" ")
        }.joinToString("\n")

        return header + "\n" + body
    }

    private fun showHelp() {
        console("""
# Help
## how to win
- when you "flag" all the cells that have mine, you win.
- if you "open" any cell that has mine before you win, the cell explodes and you lose.

## what you can do
You can choose one command to run from below every turn.

- open, open posX posY
    - open a cell which has not opened yet. If the cell has a mine in it, it will explode and the game will end as faied
    - when you don't specify position of the cell to open, you will be asked in the subsequent prompt
- flag, flag posX posY
    - flag/un-flag a cell which has not opened yet.
    - when you don't specify position of the cell to open, you will be asked in the subsequent prompt
- showMap
    - show current map
- giveUp
    - exit the game
- help
    - show this help

Enjoy!
        """.trimIndent())
    }

    private fun endTurn() {
        if(map.cleared()) {
            status = Status.CLEARED
            finish()
        }
    }

    private fun exploded() {
        status = Status.EXPLODED
        finish()
    }
    private fun giveUp() {
        status = Status.GIVEN_UP
        finish()
    }

    // interrupt用
    private fun forceShutdown() {
        status = Status.FORCE_SHUTDOWN
        finish()
    }

    // ゲーム終了処理をstatusに応じて処理
    private fun finish() {
        when(status) {
            Status.CLEARED -> println("Congratulation! You win!")
            Status.EXPLODED -> println("Unfortunately, a mine has exploded.. Please try again soon!")
            Status.GIVEN_UP -> println("You'll give up? Ok, Please try again soon!")
            Status.FORCE_SHUTDOWN -> println("force shutdown...")
            Status.PLAYING -> throw RuntimeException("reached unexpected line. The player finished the game with its status playing. Please report this issue to the game creator")
        }

        showResult()

        exitProcess(0)
    }

    private fun showResult() {
        console("""

====================
final result
====================
map:
${buildMapView()}

result: $status
turns: $numOfTurns

====================

Thank you for playing!
""".trimIndent())
    }

    private fun console(format: String, vararg args: Any?) {
        // TODO: allow developers to select how to print msgs to players
        println(String.format(format, args))
    }


    companion object {
        private val logLevel = Level.ALL
        val log = Logger.getLogger("Game")!!.apply {
            val handler = ConsoleHandler()
            val formatter = SimpleFormatter()
            handler.formatter = formatter
            handler.level = logLevel

            this.level = logLevel
            this.addHandler(handler)
        }

        private val positionPattern = Regex("""^\d+ \d+$""")
        private val commandWithPositionPattern = Regex("""^(?<command>[a-zA-Z]+)(?<position> (?<left>\d+) (?<right>\d+))?$""")
    }
}

private enum class Status {
    PLAYING,
    CLEARED,
    EXPLODED,
    GIVEN_UP,
    FORCE_SHUTDOWN,
}

private enum class Command {
    OPEN,
    FLAG,
    SHOWMAP,
    GIVEUP,
    HELP,
    INVALID
}

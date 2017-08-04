package vorpality.punting

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vorpality.algo.Punter
import vorpality.algo.RandomPunter
import vorpality.algo.SpanningTreePunter
import vorpality.protocol.*
import vorpality.protocol.Map
import vorpality.punting.GlobalSettings.MODE
import vorpality.punting.GlobalSettings.logger
import vorpality.util.Jsonable
import vorpality.util.toJsonable
import java.io.*
import java.net.Socket

enum class Mode {
    ONLINE,
    OFFLINE,
    FILE
}

enum class Punters {
    RANDOM,
    SPANNING_TREE
}

object GlobalSettings {
    var MODE = Mode.OFFLINE

    val logger: Logger = LoggerFactory.getLogger("Global")
}

class Arguments(p: ArgParser) {
    val mode by p.mapping(
            Mode.values()
                    .map { "--${it.name.toLowerCase()}" to it }
                    .toMap(),
            help = "online/offline mode"
    ).default(Mode.OFFLINE)

    val url: String by p.storing("server url")
            .default("punter.inf.ed.ac.uk")

    val port: Int by p.storing("server port") { toInt() }
            .default(9099)

    val name: String by p.storing("punter name")
            .default("301 random")

    val punter by p.mapping(
            Punters.values()
                    .map { "--${it.name.toLowerCase()}" to it }
                    .toMap(),
            help = "punter selection"
    ).default(Punters.RANDOM)

    val inputBufferSize: Int by p.storing("funking input buffer size") { toInt() }
            .default(50000)

    val input: String by p.storing("input file")
            .default("maps/sample.json")
}

inline fun <reified T : Jsonable> readJsonable(sin: Reader): T {

    var length_: String = ""

    while (true) {
        val ch = sin.read().toChar()
        if (ch != ':') length_ += ch
        else break
    }

    val length = length_.toInt()

    val contentAsArray = CharArray(length)

    var start = 0
    while (start < length) {
        val read = sin.read(contentAsArray, start, length - start)
        start += read
    }

    val content = contentAsArray.joinToString("")

    logger.info("-> $length:$content")

    val result = JsonObject(content).toJsonable<T>()
    return result
}

inline fun Jsonable.writeJsonable(sout: PrintWriter) {
    val output = prepare()

    logger.info("<- ${output.length}:$output")

    sout.print("${output.length}:$output")
    sout.flush()
}

fun main(arguments: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")

    val args = Arguments(ArgParser(arguments))

    MODE = args.mode

    logger.info("Running in $MODE mode")

    val punter = when (args.punter) {
        Punters.RANDOM -> RandomPunter()
        Punters.SPANNING_TREE -> SpanningTreePunter()
    }

    when {
        Mode.ONLINE == GlobalSettings.MODE -> runOnlineMode(args, punter, logger)
        Mode.OFFLINE == GlobalSettings.MODE -> runOfflineMode(args, punter, logger)
        Mode.FILE == GlobalSettings.MODE -> runFileMode(args, punter, logger)
    }
}

fun runFileMode(args: Arguments, punter: Punter, logger: Logger) {
    val map: Map = JsonObject(File(args.input).readText()).toJsonable()
    logger.info("map = ${map}")
    val taken: MutableMap<River, Int> = mutableMapOf()
    val adversary = RandomPunter()
    punter.setup(SetupData(0, 2, map))
    adversary.setup(SetupData(1, 2, map))

    val moves = mutableMapOf(0 to PassMove(0), 1 to PassMove(1))
    var counter = 0
    while (true) {
        val pmove = punter.step(moves.values.toList())
        pmove.claim?.apply { taken[River(source, target).sorted()] = 0 }
        moves[0] = pmove

        ++counter
        if (counter == map.rivers.size) break

        val amove = adversary.step(moves.values.toList())
        amove.claim?.apply { taken[River(source, target).sorted()] = 1 }
        moves[1] = amove

        ++counter
        if (counter == map.rivers.size) break;
    }
}

private fun runOfflineMode(args: Arguments, punter: Punter, logger: Logger) {
    val sin = BufferedReader(InputStreamReader(System.`in`), args.inputBufferSize)
    val sout = PrintWriter(System.out, true)

    // 1. Setup
    try {
        val setupData: SetupData = readJsonable(sin)
        punter.setup(setupData)
        Ready(punter.me, punter.currentState).writeJsonable(sout)
    } catch (ex: Throwable) {
        return
    }

    // 2. Gameplay

    try {
        val gtm: GameTurnMessage = readJsonable(sin)
        punter.currentState = gtm.state!!
        val step = punter.step(gtm.move.moves)
        step.copy(state = punter.currentState).writeJsonable(sout)
    } finally {
        logger.info("And that's it!")
    }
}

private fun runOnlineMode(args: Arguments, punter: Punter, logger: Logger) {
    val socker = Socket(args.url, args.port)
    val sin = BufferedReader(InputStreamReader(socker.getInputStream()), args.inputBufferSize)
    val sout = PrintWriter(socker.getOutputStream(), true)

    // 0. Handshake

    HandshakeRequest(args.name).writeJsonable(sout)

    val handshakeResponse: HandshakeResponse = readJsonable(sin)

    assert(args.name == handshakeResponse.you)

    // 1. Setup

    val setupData: SetupData = readJsonable(sin)

    punter.setup(setupData)

    Ready(punter.me).writeJsonable(sout)

    // 2. Gameplay

    try {
        while (true) {
            val gtm: GameTurnMessage = readJsonable(sin)

            val step: Jsonable = try {
                punter.step(gtm.move.moves)
            } catch(t: Throwable) {

                logger.info("Oops!", t)

                PassMove(punter.me)
            }

            step.writeJsonable(sout)
        }
    } finally {

        logger.info("And that's it!")

    }
}

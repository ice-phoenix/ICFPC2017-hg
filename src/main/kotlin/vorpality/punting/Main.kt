package vorpality.punting

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vorpality.algo.Passer
import vorpality.algo.Punter
import vorpality.algo.RandomPunter
import vorpality.algo.SpanningTreePunter
import vorpality.protocol.*
import vorpality.protocol.Map
import vorpality.punting.GlobalSettings.logger
import vorpality.sim.GraphPanel
import vorpality.sim.GraphSim
import vorpality.util.JsonObject
import vorpality.util.Jsonable
import vorpality.util.JsonableCompanion
import vorpality.util.toJsonable
import java.io.*
import java.net.Socket
import java.util.concurrent.ThreadLocalRandom
import kotlin.reflect.KClass

enum class Mode {
    ONLINE,
    OFFLINE,
    FILE
}

enum class Punters {
    RANDOM,
    SPANNING_TREE,
    PASSER
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
            help = "punting mode"
    ).default(Mode.OFFLINE)

    val url: String by p.storing("server url")
            .default("punter.inf.ed.ac.uk")

    val port: Int by p.storing("server port") {
        if (this == "random") ThreadLocalRandom.current().nextInt(9001, 9241) else toInt()
    }.default(9099)

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

    val results: String by p.storing("result trace file (empty for no trace)")
            .default("")

    val gui: Boolean by p.flagging("Enable GUI").default(false)
}

inline fun <reified T : Jsonable> readJsonable(sin: Reader): T {

    var length_: String = ""

    while (true) {
        val ch = sin.read().toChar()
        if (ch != ':') length_ += ch
        else break
    }

    val length = length_.trim().toInt()

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

    GlobalSettings.MODE = args.mode

    logger.info("Running in ${GlobalSettings.MODE} mode")

    val punter = when (args.punter) {
        Punters.RANDOM -> RandomPunter()
        Punters.SPANNING_TREE -> SpanningTreePunter()
        Punters.PASSER -> Passer()
    }

    when {
        Mode.ONLINE == GlobalSettings.MODE -> runOnlineMode(args, punter, logger)
        Mode.OFFLINE == GlobalSettings.MODE -> runOfflineMode(args, punter, logger)
        Mode.FILE == GlobalSettings.MODE -> runFileMode(args, punter, logger)
    }
}

private fun runFileMode(args: Arguments, punter: Punter, logger: Logger) {
    val map: Map = JsonObject(File(args.input).readText()).toJsonable()
    logger.info("map = ${map}")
    val taken: MutableMap<River, Int> = mutableMapOf()
    val adversary = RandomPunter()
    punter.setup(SetupData(0, 2, map, null))
    adversary.setup(SetupData(1, 2, map, null))

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
    val sin = InputStreamReader(System.`in`)
    val sout = PrintWriter(System.out, true)

    // 0. Handshake

    HandshakeRequest(args.name).writeJsonable(sout)
    val handshakeResponse: HandshakeResponse = readJsonable(sin)
    assert(args.name == handshakeResponse.you)

    val message: Message = readJsonable(sin)

    // 1. Stuff
    if (message.setupData != null) {
        punter.setup(message.setupData)
        Ready(punter.me, state = punter.currentState).writeJsonable(sout)
        return
    } else if (message.turn != null) {
        try {
            val gtm = message.turn
            punter.currentState = gtm.state!!
            val step = punter.step(gtm.move.moves)
            step.copy(state = punter.currentState).writeJsonable(sout)
        } catch (ex: Exception) {
            PassMove(punter.me, state = punter.currentState).writeJsonable(sout)
        }
        return
    } else if (message.end != null) {
        val res = message.end
        message.end.state?.let { punter.currentState = it }
        logger.info("And that's it!")
        logger.info("Result: $res")
        val myScore = res.stop.scores.find { it.punter == punter.me }?.score
        val maxScore = res.stop.scores.map { it.score }.max()
        logger.info("My score: $myScore")
        logger.info("Did we win? (${if (myScore == maxScore) "Oh yeah!" else "Nope :-("})")
    }

}

fun <T> tryOrNull(body: () -> T) = try {
    body()
} catch (ex: Exception) {
    null
}

data class Message(
        val turn: GameTurnMessage? = null,
        val end: GameResult? = null,
        val setupData: SetupData? = null,
        val timeout: Timeout? = null) : Jsonable {
    override fun toJson(): JsonObject {
        return turn?.toJson() ?: end?.toJson() ?: setupData?.toJson() ?: timeout?.toJson() ?: throw IllegalStateException()
    }

    companion object : JsonableCompanion<Message> {
        override val dataklass: KClass<Message> get() = throw NotImplementedError()

        override fun fromJson(json: JsonObject): Message? {
            return tryOrNull {
                Message(turn = json.toJsonable())
            } ?: tryOrNull {
                Message(end = json.toJsonable())
            } ?: tryOrNull {
                Message(setupData = json.toJsonable())
            } ?: tryOrNull {
                Message(timeout = json.toJsonable())
            } ?: throw IllegalArgumentException("${json}")
        }
    }
}

private fun runOnlineMode(args: Arguments, punter: Punter, logger: Logger) {
    logger.info("Connecting to ${args.url}:${args.port}")
    val socker = Socket(args.url, args.port)
    val sin = BufferedReader(InputStreamReader(socker.getInputStream()), args.inputBufferSize)
    val sout = PrintWriter(socker.getOutputStream(), true)

    // 0. Handshake

    HandshakeRequest(args.name).writeJsonable(sout)

    val handshakeResponse: HandshakeResponse = readJsonable(sin)

    assert(args.name == handshakeResponse.you)

    // 1. Setup

    val setupData: SetupData = readJsonable(sin)

    val sim = if(args.gui) GraphSim(setupData.map) else null
    punter.setup(setupData)
    val gui = if(args.gui) {
        GraphPanel(sim!!, punter, setupData.punters).apply {
            showMe()
            repaint()
        }
    } else null

    Ready(punter.me).writeJsonable(sout)

    // 2. Gameplay

    var res: GameResult?
    while (true) {
        val serverMessage: Message = readJsonable(sin)

        res = serverMessage.end
        if (res != null) break

        if(serverMessage.timeout != null) {
            logger.info("timeout", Exception())
            continue
        }
        val gtm = serverMessage.turn ?: throw IllegalStateException()

        val step: Jsonable = try {
            for(move in gtm.move.moves) sim?.handleMove(move)
            gui?.repaint()

            punter.step(gtm.move.moves)
        } catch(t: Throwable) {

            logger.info("Oops!", t)

            PassMove(punter.me)
        }

        step.writeJsonable(sout)
    }

    logger.info("And that's it!")
    logger.info("Result: ${res?.stop?.scores?.joinToString("\n", prefix = "\n") { "${it.punter}: ${it.score}" }}")
    res ?: return
    val myScore = res.stop.scores.find { it.punter == punter.me }?.score
    val maxScore = res.stop.scores.map { it.score }.max()
    logger.info("My score: $myScore")
    logger.info("Did we win? (${if (myScore == maxScore) "Oh yeah!" else "Nope :-("})")

    if(args.results != "") {
        File(args.results).appendText(
                JsonObject("score" to myScore, "win" to (myScore == maxScore), "url" to args.url, "port" to args.port).encodePrettily() + ",\n"
        )
    }


}

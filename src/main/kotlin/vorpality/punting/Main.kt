package vorpality.punting

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.intellij.lang.annotations.Language
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
import vorpality.util.*
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

    const val logging = true
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

    val splurging: Boolean by p.flagging("Enable splurge moves").default(false)
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

    logger.info("Reading on")

    var start = 0
    while (start < length) {
        val read = sin.read(contentAsArray, start, length - start)
        start += read
    }

    logger.info("Reading off")

    val content = String(contentAsArray)

    if (GlobalSettings.logging) {
        logger.info("-> $length:$content")
    }

    logger.info("JsonObject on")
    val result = JsonObject(content)
    logger.info("JsonObject off")
    logger.info("toJsonable on")
    val result2 = result.toJsonable<T>()
    logger.info("toJsonable off")
    return result2
}

inline fun Jsonable.writeJsonable(sout: PrintWriter) {
    val output = prepare()

    if (GlobalSettings.logging) {
        logger.info("<- ${output.length}:$output")
    }

    sout.print("${output.length}:$output")
    sout.flush()
}

fun main(arguments: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")

    logger.info("Starting")

    val args = Arguments(ArgParser(arguments))

    GlobalSettings.MODE = args.mode

    logger.info("Warming up")
    warmUp()
    logger.info("Warming done")

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

fun warmUp(punter: Punter) {
    val mes: Message = JsonObject(//language=JSON
            """
{
        "punter" : 0,
        "punters" : 15,
        "map" : {   "sites": [     {"id": 0, "x": 0.0, "y": 0.0},     {"id": 1, "x": 1.0, "y": 0.0},     {"id": 2, "x": 2.0, "y": 0.0},     {"id": 3, "x": 2.0, "y": -1.0},     {"id": 4, "x": 2.0, "y": -2.0},     {"id": 5, "x": 1.0, "y": -2.0},     {"id": 6, "x": 0.0, "y": -2.0},     {"id": 7, "x": 0.0, "y": -1.0}   ],   "rivers": [     { "source": 0, "target": 1},     { "source": 1, "target": 2},     { "source": 0, "target": 7},     { "source": 7, "target": 6},     { "source": 6, "target": 5},     { "source": 5, "target": 4},     { "source": 4, "target": 3},     { "source": 3, "target": 2},     { "source": 1, "target": 7},     { "source": 1, "target": 3},     { "source": 7, "target": 5},     { "source": 5, "target": 3}   ],   "mines": [1, 5] }
}
"""
    ).toJsonable()

    val inp = mes.setupData!!.toJson().encode()
    val sd: SetupData = JsonObject(inp).toJsonable()
    punter.setup(sd)
    val rd = punter.currentState.encode()
    punter.currentState = JsonObject(rd)

    val gtm: GameTurnMessage = JsonObject(//language=JSON
            """
            {
                "move": {
                  "moves": [
                    { "claim" : {"punter":0, "source": 1, "target": 2} },
                    { "pass" : { "punter" : 1 } }
                  ]
                }
            }
"""
    ).toJsonable<GameTurnMessage>()
    readJsonable<GameTurnMessage>(StringReader(gtm.toJson().encode().let { "${it.length}:$it" }))

    punter.step(gtm.move.moves)

}

fun warmUp() {
    warmUp(SpanningTreePunter())
    warmUp(RandomPunter())
    warmUp(Passer())

    readJsonable<Ready>(StringReader(Ready(0, listOf(Future(0, 3), Future(0, 5)), JsonObject("{}")).toJson().encode().let { "${it.length}:$it" }))
    readJsonable<HandshakeResponse>(StringReader(HandshakeResponse("lorem ipsum").toJson().encode().let { "${it.length}:$it" }))

}

private fun runFileMode(args: Arguments, punter: Punter, logger: Logger) {
    val map: Map = JsonObject(File(args.input).readText()).toJsonable()
    // logger.info("map = ${map}")
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

    logger.info("TIMING START")

    val message: Message = readJsonable(sin)

    // 1. Stuff
    if (message.setupData != null) {
        if(!args.splurging) message.setupData.settings?.set("splurges", false)
        punter.setup(message.setupData)
        Ready(punter.me, state = punter.currentState).writeJsonable(sout)
    } else if (message.turn != null) {
        try {
            val gtm = message.turn
            punter.currentState = gtm.state!!
            val step = punter.step(gtm.move.moves)
            step.copy(state = punter.currentState).writeJsonable(sout)
        } catch (ex: Exception) {
            logger.info("Oops!", ex)
            PassMove(punter.me, state = punter.currentState).writeJsonable(sout)
        }
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

    logger.info("TIMING END")

    return

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
            return when {
                json.containsKey("move") -> Message(turn = json.toJsonable())
                json.containsKey("stop") -> Message(end = json.toJsonable())
                json.containsKey("punters") -> Message(setupData = json.toJsonable())
                json.containsKey("timeout") -> Message(timeout = json.toJsonable())
                else -> throw IllegalArgumentException("$json")
            }
//            return tryOrNull {
//                Message(turn = json.toJsonable())
//            } ?: tryOrNull {
//                Message(end = json.toJsonable())
//            } ?: tryOrNull {
//                Message(setupData = json.toJsonable())
//            } ?: tryOrNull {
//                Message(timeout = json.toJsonable())
//            } ?: throw IllegalArgumentException("$json")
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

    if(!args.splurging) setupData.settings?.set("splurges", false)

    val sim = if (args.gui) GraphSim(setupData.map) else null
    punter.setup(setupData)
    val gui = if (args.gui) {
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

        if (serverMessage.timeout != null) {
            logger.info("timeout", Exception())
            continue
        }
        val gtm = serverMessage.turn ?: throw IllegalStateException()

        val step: Jsonable = try {
            for (move in gtm.move.moves) sim?.handleMove(move)
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

    if (args.results != "") {
        File(args.results).appendText(
                JsonObject("score" to myScore, "win" to (myScore == maxScore), "url" to args.url, "port" to args.port).encodePrettily() + ",\n"
        )
    }


}

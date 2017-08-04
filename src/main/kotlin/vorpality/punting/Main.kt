package vorpality.punting

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vorpality.algo.RandomPunter
import vorpality.algo.SpanningTreePunter
import vorpality.protocol.*
import vorpality.punting.GlobalSettings.MODE
import vorpality.punting.GlobalSettings.logger
import vorpality.util.Jsonable
import vorpality.util.toJsonable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.Reader
import java.net.Socket

enum class Mode {
    ONLINE,
    OFFLINE
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

    if (Mode.ONLINE == MODE) {
        val socker = Socket(args.url, args.port)
        val sin = BufferedReader(InputStreamReader(socker.getInputStream()), args.inputBufferSize)
        val sout = PrintWriter(socker.getOutputStream(), true)

        // 0. Handshake

        HandshakeRequest(args.name).writeJsonable(sout)

        val handshakeResponse: HandshakeResponse = readJsonable(sin)

        assert(args.name == handshakeResponse.you)

        // 1. Setup

        val setupData: SetupData = readJsonable(sin)

        val punter = when (args.punter) {
            Punters.RANDOM -> RandomPunter()
            Punters.SPANNING_TREE -> SpanningTreePunter()
        }
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
}

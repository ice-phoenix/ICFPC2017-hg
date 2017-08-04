package vorpality.punting

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import org.slf4j.LoggerFactory

enum class Mode {
    ONLINE,
    OFFLINE
}

object GlobalSettings {
    var MODE = Mode.OFFLINE
}

class Arguments(p: ArgParser) {
    val mode by p.mapping(
            Mode.values()
                    .map { "--${it.name.toLowerCase()}" to it }
                    .toMap(),
            help = "punting mode"
    ).default(Mode.OFFLINE)
}

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")

    val arguments = Arguments(ArgParser(args))

    GlobalSettings.MODE = arguments.mode

    logger.info("Running in ${GlobalSettings.MODE} mode")
}

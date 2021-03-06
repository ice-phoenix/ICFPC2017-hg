package vorpality.algo

import grph.Grph
import grph.in_memory.InMemoryGrph
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import toools.set.IntSet
import vorpality.protocol.SetupData
import vorpality.punting.GlobalSettings
import vorpality.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.reflect

const val EMPTY_COLOR = -1
const val MINE_COLOR = 1

fun <T> decltype(witness: () -> T): KType = witness.reflect()!!.returnType
fun <T> Any?.tryFromJsonWithTypeOf(witness: () -> T) = tryFromJson(decltype(witness)) as T

fun Grph.calcScores(mines: IntSet): Map<Pair<Int, Int>, Int> {
    val score = mutableMapOf<Pair<Int, Int>, Int>()
    val bfs = mines.map { (mine) -> bfs(mine) }

    for ((i, mine) in mines.withIndex()) {
        for ((j, dist) in bfs[i].distances.withIndex()) if (dist != -1) {
            score[mine.value to j] = dist * dist
        }
    }

    return score
}

object GrphJsoner {
    fun toJson(graph: Grph): Any? {
        val edges = graph.edges.toIntArray()
        val edgesAsList = edges
                .asSequence()
                .map { e -> e to graph.getOneVertex(e) }
                .flatMap { (e, from) -> sequenceOf(e, from, graph.getTheOtherVertex(e, from)) }
                .toList()
        return JsonArray(edgesAsList)
    }

    fun fromJson(js: Any?): Grph {
        val grph = InMemoryGrph()

        val edgesAsList = (js as JsonArray)

        for (i in 0..edgesAsList.size() - 1 step 3) {
            grph.addSimpleEdge(
                    edgesAsList.getInteger(i + 1),
                    edgesAsList.getInteger(i),
                    edgesAsList.getInteger(i + 2),
                    false
            )
        }
        return grph
    }
}

abstract class AbstractPunter : Punter {

    override var currentScore: Int? = null

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    protected class State(val graph: Grph,
                          var originalGraph: Grph = graph,
                          val ownerColoring: MutableMap<Int, Int> = mutableMapOf(),
                          val mineColoring: MutableMap<Int, Int> = mutableMapOf(),
                          var scoring: Map<Pair<Int, Int>, Int> = mutableMapOf(),
                          var optionEdges: Set<Int> = mutableSetOf()) : Jsonable {

        constructor(data: SetupData) : this(InMemoryGrph()) {
            with(data.map) {
                for ((id) in sites) {
                    graph.addVertex(id)
                }
                for ((from, to) in rivers) {
                    graph.addSimpleEdge(from, to, false)
                }
                for (mine in mines) {
                    mineColoring[mine] = MINE_COLOR
                }
            }
            originalGraph = graph.clone()
            scoring = originalGraph.calcScores(
                    mineColoring
                            .asSequence()
                            .filter { (_, v) -> v == MINE_COLOR }
                            .map { (k, _) -> k }
                            .toIntSet()
            )
        }

        init {
            scoring = originalGraph.calcScores(
                    mineColoring
                            .asSequence()
                            .filter { (_, v) -> v == MINE_COLOR }
                            .map { (k, _) -> k }
                            .toIntSet()
            )
        }

        override fun toJson(): JsonObject {
            return JsonObject(
                    "graph" to GrphJsoner.toJson(graph),
                    "originalGraph" to GrphJsoner.toJson(originalGraph),
                    "mineColoring" to mineColoring.tryToJson(),
                    "ownerColoring" to ownerColoring.tryToJson(),
                    "optionEdges" to optionEdges.tryToJson()
            )
        }

        companion object : JsonableCompanion<State> {
            override val dataklass: KClass<State> = State::class
            override fun fromJson(json: JsonObject): State {
                return State(
                        graph = GrphJsoner.fromJson(json.get("graph")),
                        originalGraph = GrphJsoner.fromJson(json.get("originalGraph")),
                        mineColoring = json.get("mineColoring").tryFromJsonWithTypeOf { mutableMapOf(1 to 2) },
                        ownerColoring = json.get("ownerColoring").tryFromJsonWithTypeOf { mutableMapOf(1 to 2) },
                        optionEdges = json.get("optionEdges").tryFromJsonWithTypeOf { mutableSetOf(1) }
                )
            }

        }
    }

    override var me: Int = -1
    var credit: Int = Int.MIN_VALUE
    var optionsEnabled: Boolean = false
    var myOptionsCount: Int = 0

    override var currentState: JsonObject
        get() {
            logger.info("Storing on")
            val res = state.toJson().apply {
                put("me", me)
                        .put("credit", credit)
                        .put("optionsEnabled", optionsEnabled)
                        .put("myOptionsCount", myOptionsCount)
            }
            logger.info("Storing off")
            return res
        }
        set(value) {
            logger.info("Loading on")
            me = value.getInteger("me")
            credit = value.getInteger("credit")
            optionsEnabled = value.getBoolean("optionsEnabled")
            myOptionsCount = value.getInteger("myOptionsCount")
            state = value.toJsonable<State>()
            logger.info("Loading off")
        }

    protected lateinit var state: State

    protected val mines: IntArray by lazy {
        with(state) {
            mineColoring
                    .asSequence()
                    .filter { it.value == MINE_COLOR }
                    .map { it.key }
                    .toList()
                    .toIntArray()
        }
    }

    override fun setup(data: SetupData) {
        me = data.punter
        credit = if (data.settings?.getBoolean("splurges") ?: false) -1 else Int.MIN_VALUE
        optionsEnabled = data.settings?.getBoolean("options") ?: false
        myOptionsCount = 0
        state = State(data)

        // Disable splurging on small graphs
        if (state.graph.size / data.punters < 50) {
            credit = Int.MIN_VALUE
        }

        if (GlobalSettings.logging) {
            logger.info("Graph is: ${state.graph.toGrphText()}")
        }
    }

}

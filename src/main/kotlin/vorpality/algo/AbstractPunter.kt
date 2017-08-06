package vorpality.algo

import grph.Grph
import grph.algo.search.BFSAlgorithm
import grph.algo.search.GraphSearchListener
import grph.in_memory.InMemoryGrph
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import toools.set.IntSet
import vorpality.protocol.SetupData
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

    for((i, mine) in mines.withIndex()) {
        for((j, dist) in bfs[i].distances.withIndex()) if(dist != -1) {
            score[mine.value to j] = dist * dist
        }
    }

    return score
}

abstract class AbstractPunter : Punter {

    override var currentScore: Int? = null

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    protected class State(val graph: Grph,
                          val ownerColoring: MutableMap<Int, Int> = mutableMapOf(),
                          val mineColoring: MutableMap<Int, Int> = mutableMapOf(),
                          var scoring: Map<Pair<Int, Int>, Int> = mutableMapOf()) : Jsonable {
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
            scoring = graph.calcScores(mineColoring.asSequence()
                    .filter { (_, v) -> v == MINE_COLOR }
                    .map{ (k, _) -> k}
                    .toIntSet())
        }

        init {
            scoring = graph.calcScores(mineColoring.asSequence()
                    .filter { (_, v) -> v == MINE_COLOR }
                    .map{ (k, _) -> k}
                    .toIntSet())
        }

        override fun toJson(): JsonObject {
            val edges = graph.edges.toIntArray()
            val edgeMap = edges
                    .asSequence()
                    .map { e -> e to graph.getOneVertex(e) }
                    .map { (e, from) -> e to (from to graph.getTheOtherVertex(e, from)) }
                    .toMap()

            return JsonObject(
                    "graph" to edgeMap.tryToJson(),
                    "mineColoring" to mineColoring.tryToJson(),
                    "ownerColoring" to ownerColoring.tryToJson(),
                    "scoring" to scoring.tryToJson()
            )
        }

        companion object : JsonableCompanion<State> {
            override val dataklass: KClass<State> = State::class
            override fun fromJson(json: JsonObject): State {
                val grph = InMemoryGrph()

                val edgeMap = json.get("graph").tryFromJsonWithTypeOf { mutableMapOf(1 to (2 to 3)) }

                for ((e, p) in edgeMap) {
                    grph.addSimpleEdge(p.first, e, p.second, false)
                }

                return State(
                        graph = grph,
                        mineColoring = json.get("mineColoring").tryFromJsonWithTypeOf { mutableMapOf(1 to 2) },
                        ownerColoring = json.get("ownerColoring").tryFromJsonWithTypeOf { mutableMapOf(1 to 2) },
                        scoring = json.get("scoring").tryFromJsonWithTypeOf { mutableMapOf((1 to 2) to 3) }
                )
            }

        }
    }



    override var me: Int = -1
    var credit: Int = Int.MIN_VALUE

    override var currentState: JsonObject
        get() = state.toJson().apply { put("me", me).put("credit", credit) }
        set(value) {
            me = value.getInteger("me")
            credit = value.getInteger("credit")
            state = value.toJsonable<State>()
        }
    protected lateinit var state: State

    protected val mines: IntArray by lazy {
        with(state) {
            mineColoring
                    .filter { it.value == MINE_COLOR }
                    .map { it.key }
                    .toIntArray()
        }
    }

    override fun setup(data: SetupData) {
        me = data.punter
        credit = if (data.settings?.getBoolean("splurges") ?: false) -1 else Int.MIN_VALUE
        state = State(data)

        logger.info("Graph is: ${state.graph.toGrphText()}")
    }

}

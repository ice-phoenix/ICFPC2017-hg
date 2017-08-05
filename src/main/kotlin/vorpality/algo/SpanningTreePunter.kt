package vorpality.algo

import grph.Grph
import grph.path.Path
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import toools.set.IntArrayWrappingIntSet
import vorpality.protocol.ClaimMove
import vorpality.protocol.Move
import vorpality.protocol.PassMove
import vorpality.protocol.SetupData
import vorpality.util.tryToJson
import java.util.concurrent.ThreadLocalRandom
import kotlin.collections.component1
import kotlin.collections.component2

class SpanningTreePunter : AbstractPunter() {

    private var minePairs: MutableList<Pair<Int, Int>> = mutableListOf()

    override fun setup(data: SetupData) {
        super.setup(data)

        with(state) {
            val paths = mutableMapOf<Pair<Int, Int>, Int>()

            for (from in mines) {
                for (to in mines) {
                    if (from >= to) continue

                    val path = graph.getShortestPath(from, to)

                    paths.put(from to to, path.length)
                }
            }

            minePairs = paths.toList()
                    .sortedBy { it.second }
                    .map { it.first }
                    .toMutableList()
        }
    }

    private fun Path.isInteresting(
            grph: Grph,
            pred: (Int) -> Boolean): Int {

        val asArray = toVertexArray()

        var selectedEdge: Int = -1

        var prevVertex = asArray.first()
        for (nextVertex in asArray.drop(1)) {
            val currentEdge = grph.getSomeEdgeConnecting(prevVertex, nextVertex)

            if (pred(currentEdge)) {
                selectedEdge = currentEdge
                break
            }

            prevVertex = nextVertex
        }

        return selectedEdge
    }

    private fun EmptyEdgePredicate(edge: Int): Boolean =
            with(state) {
                EMPTY_COLOR == ownerColoring.getOrDefault(edge, EMPTY_COLOR)
            }

    override fun step(moves: List<Move>): Move {
        with(state) {
            for ((_, claim) in moves) {
                claim ?: continue

                val edge = graph.getSomeEdgeConnecting(claim.source, claim.target)

                if (me == claim.punter) {
                    ownerColoring[edge] = me
                } else {
                    graph.removeEdge(edge)
                }
            }

            logger.info("Mine pairs: $minePairs")

            logger.info("Mine color: $mineColoring")
            logger.info("Owner color: $ownerColoring")

            if (minePairs.isEmpty()) {

                val rnd = ThreadLocalRandom.current()

                var newPairs = graph
                        .connectedComponents
                        .filter { it.contains(IntArrayWrappingIntSet(mines)) }
                        .map { graph.getSubgraphInducedByVertices(it) }
                        .map { scc -> scc to (mines.filter { scc.containsVertex(it) }.firstOrNull() ?: scc.vertices.pickRandomElement(rnd)) }
                        .map { (scc, from) -> scc to (from to scc.getFartestVertex(from)) }
                        .filter { (_, p) -> p.first != p.second }
                        .filter { (scc, p) ->
                            -1 != scc.spanningTree
                                    .getShortestPath(p.first, p.second)
                                    .isInteresting(
                                            graph,
                                            this@SpanningTreePunter::EmptyEdgePredicate
                                    )
                        }
                        .map { (_, p) -> p }
                        .toMutableList()

                if (newPairs.isEmpty()) {
                    // Try our best inside the SCCs

                    newPairs = graph
                            .connectedComponents
                            .map { graph.getSubgraphInducedByVertices(it) to it.pickRandomElement(rnd) }
                            .map { (scc, from) -> scc to (from to scc.getFartestVertex(from)) }
                            .filter { (_, p) -> p.first != p.second }
                            .filter { (scc, p) ->
                                -1 != scc.spanningTree
                                        .getShortestPath(p.first, p.second)
                                        .isInteresting(
                                                graph,
                                                this@SpanningTreePunter::EmptyEdgePredicate
                                        )
                            }
                            .map { (_, p) -> p }
                            .toMutableList()
                }

                if (newPairs.isEmpty()) {
                    // Bailing out

                    return PassMove(me)
                }

                minePairs = newPairs
            }

            val (activePath, scc) = minePairs
                    .asSequence()
                    .map { path ->
                        path to graph.getConnectedComponentContaining(
                                path.first, Grph.DIRECTION.in_out)
                    }
                    .filter { (path, scc) -> scc.contains(path.second) }
                    .firstOrNull()
                    ?: run {

                // minePairs are either disconnected or empty
                // need to switch to another strategy
                // retry with empty minePairs handles this

                minePairs.clear()

                return step(emptyList())
            }

            val spanningTree = graph
                    .getSubgraphInducedByVertices(scc)
                    .spanningTree

            val currentPath = spanningTree
                    .getShortestPath(activePath.first, activePath.second)

            logger.info("Path: $currentPath")

            val selectedEdge = currentPath.isInteresting(
                    spanningTree,
                    this@SpanningTreePunter::EmptyEdgePredicate
            )

            return if (-1 != selectedEdge) {
                val (from, to) = spanningTree
                        .getVerticesIncidentToEdge(selectedEdge)
                        .toIntArray()
                ClaimMove(me, to, from)
            } else {
                minePairs.removeAt(0)

                // retry again
                step(emptyList())
            }
        }
    }

    override var currentState: JsonObject
        get() = super.currentState.apply { put("minePairs", minePairs.tryToJson()) }
        set(value) {
            super.currentState = value
            minePairs.clear()
            minePairs = value.getJsonArray("minePairs").mapTo(minePairs) {
                when (it) {
                    is JsonArray -> it.getInteger(0) to it.getInteger(1)
                    else -> throw IllegalArgumentException()
                }
            }
        }

}

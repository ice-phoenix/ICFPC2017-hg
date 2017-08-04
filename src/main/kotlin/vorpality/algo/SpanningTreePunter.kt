package vorpality.algo

import grph.Grph
import io.vertx.core.json.JsonObject
import toools.set.IntSingletonSet
import vorpality.protocol.ClaimMove
import vorpality.protocol.Move
import vorpality.protocol.PassMove
import vorpality.protocol.SetupData

class SpanningTreePunter : AbstractPunter() {

    private lateinit var minePairs: MutableList<Pair<Int, Int>>

    override fun setup(data: SetupData) {
        super.setup(data)

        with(state) {
            val mines = mineColoring.findElementsWithValue(
                    MINE_COLOR.toLong(), graph.vertices
            ).toIntArray()

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

    override fun step(moves: List<Move>): Move {
        with(state) {
            for ((_, claim) in moves) {
                claim ?: continue

                val edge = graph.getSomeEdgeConnecting(claim.source, claim.target)

                if (me == claim.punter) {
                    graph.highlightEdges(IntSingletonSet(edge), me)
                } else {
                    graph.removeEdge(edge)
                }
            }

            logger.info("Mine pairs: $minePairs")

            val (activePath, scc) = minePairs
                    .asSequence()
                    .map { path ->
                        path to graph.getConnectedComponentContaining(
                                path.first, Grph.DIRECTION.in_out)
                    }
                    .filter { (path, scc) -> scc.contains(path.second) }
                    .firstOrNull() ?: return PassMove(me)

            val spanningTree = graph
                    .getSubgraphInducedByVertices(scc)
                    .spanningTree
                    .apply { setEdgesColor(ownerColoring) }

            val currentPath = spanningTree
                    .getShortestPath(activePath.first, activePath.second)

            var selectedEdge: Int = -1

            var prevVertex = activePath.first
            for (nextVertex in currentPath.toVertexArray().drop(1)) {
                val currentEdge = spanningTree.getSomeEdgeConnecting(prevVertex, nextVertex)

                if (EMPTY_COLOR == ownerColoring.getValueAsInt(currentEdge)) {
                    selectedEdge = currentEdge
                    break
                }

                prevVertex = nextVertex
            }

            return if (-1 != selectedEdge) {
                val (from, to) = spanningTree
                        .getVerticesIncidentToEdge(selectedEdge)
                        .toIntArray()
                ClaimMove(me, to, from)
            } else {
                minePairs.removeAt(0)
                PassMove(me)
            }
        }
    }

    override var currentState: JsonObject
        get() = TODO("not implemented")
        set(value) = TODO("not implemented")

}

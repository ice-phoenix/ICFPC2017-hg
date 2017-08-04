package vorpality.algo

import grph.Grph
import toools.set.IntArrayWrappingIntSet
import toools.set.IntSingletonSet
import vorpality.protocol.ClaimMove
import vorpality.protocol.Move
import vorpality.protocol.PassMove
import vorpality.protocol.SetupData
import vorpality.util.Jsonable
import java.util.concurrent.ThreadLocalRandom

class SpanningTreePunter : AbstractPunter() {

    private lateinit var minePairs: MutableList<Pair<Int, Int>>

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

            if (minePairs.isEmpty()) {

                var newPairs = graph
                        .connectedComponents
                        .filter { it.contains(IntArrayWrappingIntSet(mines)) }
                        .map { graph.getSubgraphInducedByVertices(it) }
                        .map { scc -> scc to mines.filter { scc.containsVertex(it) }.first() }
                        .map { (scc, from) -> from to scc.getFartestVertex(from) }
                        .filter { (from, to) -> from != to }
                        .toMutableList()

                if (newPairs.isEmpty()) {
                    // Try our best inside the SCCs
                    val rnd = ThreadLocalRandom.current()

                    newPairs = graph
                            .connectedComponents
                            .map { graph.getSubgraphInducedByVertices(it) to it.pickRandomElement(rnd) }
                            .map { (scc, from) -> from to scc.getFartestVertex(from) }
                            .filter { (from, to) -> from != to }
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

                // retry again
                step(emptyList())
            }
        }
    }

    override val currentState: Jsonable
        get() = TODO("not implemented")

}

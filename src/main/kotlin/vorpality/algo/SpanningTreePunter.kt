package vorpality.algo

import grph.Grph
import grph.path.Path
import grph.properties.NumericalProperty
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import toools.set.IntArrayWrappingIntSet
import toools.set.IntSet
import toools.set.IntSets
import vorpality.protocol.*
import vorpality.punting.GlobalSettings
import vorpality.util.tryToJson
import java.util.concurrent.ThreadLocalRandom

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

            minePairs = paths
                    .asSequence()
                    .sortedBy { it.value }
                    .map { it.key }
                    .toMutableList()
        }
    }

    private fun Path.isInteresting(
            grph: Grph,
            pred: (Int) -> Boolean): List<List<Pair<Int, Int>>> {

        val asArray = toVertexArray()

        val selectedEdges = mutableListOf<List<Pair<Int, Int>>>()

        var currentPath = emptyList<Pair<Int, Int>>()

        var prevVertex = asArray.first()
        for (nextVertex in asArray.drop(1)) {
            val currentEdge = grph.getSomeEdgeConnecting(prevVertex, nextVertex)

            if (pred(currentEdge)) {
                currentPath += (prevVertex to nextVertex)
            } else if (currentPath.isNotEmpty()) {
                selectedEdges += currentPath
                currentPath = emptyList()
            }

            prevVertex = nextVertex
        }

        if (currentPath.isNotEmpty()) {
            selectedEdges += currentPath
        }

        return selectedEdges
    }

    private fun EmptyEdgePredicate(edge: Int): Boolean =
            with(state) {
                EMPTY_COLOR == ownerColoring.getOrDefault(edge, EMPTY_COLOR)
            }

    private fun score(grph: Grph) = grph
            .vertices
            .asSequence()
            .map { (v) ->
                mines.asSequence()
                        .filter { grph.containsVertex(it) && grph.containsAPath(it, v) }
                        .sumBy { state.scoring[it to v] ?: 0 }
            }.sum()

    private fun scoreIn(grph: Grph, vararg vs: Int) = vs.sumBy { v ->
        mines.asSequence()
                .filter { grph.containsVertex(it) }
                .sumBy { state.scoring[it to v] ?: 0 }
    }

    override fun step(moves: List<Move>): Move {
        with(state) {
            for ((pass, claim, splurge) in moves) {
                if (pass != null) {
                    if (me == pass.punter) credit++
                    continue
                }

                if (claim != null) {
                    val edge = graph.getSomeEdgeConnecting(claim.source, claim.target)

                    if (me == claim.punter) {
                        ownerColoring[edge] = me
                    } else {
                        graph.removeEdge(edge)
                    }
                }

                if (splurge != null) {

                    val edges = mutableListOf<Int>()

                    var currentSite = splurge.route.first()
                    for (nextSite in splurge.route.drop(1)) {
                        edges.add(graph.getSomeEdgeConnecting(currentSite, nextSite))
                        currentSite = nextSite
                    }

                    if (me == splurge.punter) {
                        for (edge in edges) {
                            ownerColoring[edge] = me
                        }
                    } else {
                        for (edge in edges) {
                            graph.removeEdge(edge)
                        }
                    }
                }
            }

            if (GlobalSettings.logging) {
                logger.info("Mine pairs: $minePairs")
                logger.info("Mine color: $mineColoring")
                logger.info("Owner color: $ownerColoring")
            }

            val rnd = ThreadLocalRandom.current()

            val ourEdges = state.ownerColoring.filter { it.value == me }.keys.toIntSet()

            val ourEdgesFirstPriority = NumericalProperty(null, 32, 1)
                    .apply { ourEdges.forEach { setValue(it.value, 0) } }
            val ourVertices = ourEdges
                    .flatMap { graph.edgeVertices(it.value).toList() }
                    .toIntSet()

            val ourGraph = graph.getSubgraphInducedByEdges(ourEdges)
            logger.info("Scoring on")
            currentScore = score(ourGraph)
            logger.info("Scoring off")

            if (GlobalSettings.logging) {
                logger.info("Our current score is: $currentScore")
            }

            if (minePairs.isEmpty()) {

                var newPairs = graph
                        .connectedComponents
                        .asSequence()
                        .filter { !IntSets.intersection(it, IntArrayWrappingIntSet(mines)).isEmpty }
                        .map { graph.getSubgraphInducedByVertices(it) }
                        .map { scc ->
                            scc to
                                    (mines.filter { scc.containsVertex(it) }.maxBy { scoreIn(ourGraph, it) }
                                            ?: scc.vertices.pickRandomElement(rnd))
                        }
                        .map { (scc, from) ->
                            scc to
                                    (from to
                                            scc.vertices.maxBy { scoreIn(ourGraph, from, it.value) }!!.value)
                        }
                        .filter { (_, p) -> p.first != p.second }
                        .filter { (scc, p) ->
                            scc.spanningTree
                                    .getShortestPath(p.first, p.second, ourEdgesFirstPriority)
                                    .isInteresting(
                                            graph,
                                            this@SpanningTreePunter::EmptyEdgePredicate
                                    )
                                    .isNotEmpty()
                        }
                        .map { (_, p) -> p }
                        .toMutableList()

                if (newPairs.isEmpty()) {
                    // Try our best inside the SCCs

                    newPairs = graph
                            .connectedComponents
                            .asSequence()
                            .filter { !IntSets.intersection(it, IntArrayWrappingIntSet(mines)).isEmpty }
                            .map {
                                graph.getSubgraphInducedByVertices(it) to
                                        IntSets.intersection(it, ourVertices)
                                                .maxBy { scoreIn(ourGraph, it.value) }
                            }
                            .map { (scc, v) -> v?.let { scc to v.value } }
                            .filterNotNull()
                            .map { (scc, from) ->
                                scc to
                                        (from to
                                                IntSets.difference(scc.vertices, ourVertices)
                                                        .maxBy { scoreIn(ourGraph, from, it.value) }?.value)
                            }
                            .filter { (_, p) -> p.first != p.second && p.second != null }
                            .map { it.uncheckedCast<Pair<Grph, Pair<Int, Int>>>() }
                            .filter { (scc, p) ->
                                scc.spanningTree
                                        .getShortestPath(p.first, p.second, ourEdgesFirstPriority)
                                        .isInteresting(
                                                graph,
                                                this@SpanningTreePunter::EmptyEdgePredicate
                                        )
                                        .isNotEmpty()
                            }
                            .map { (_, p) -> p }
                            .toMutableList()
                }

                if (newPairs.isEmpty()) {
                    // Bailing out

                    return PassMove(me)
                }

                minePairs = newPairs
                sortMinePairs(ourVertices)
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

                logger.info("Retrying with non-empty mine pairs")

                return step(emptyList())
            }

            minePairs = minePairs.dropWhile { it !== activePath }.toMutableList()

            val spanningTree = graph
                    .getSubgraphInducedByVertices(scc)
                    .spanningTree

            val noShuffle = when (mineColoring[activePath.first] to mineColoring[activePath.second]) {
                Pair(MINE_COLOR, MINE_COLOR) -> // first build at least one edge near each mine, then proceed
                    when {
                    // first is already ours, start with second => shuffle
                        activePath.first in ourVertices && activePath.second !in ourVertices -> false
                    // second is already ours, start with first => no shuffle
                        activePath.second in ourVertices && activePath.first !in ourVertices -> true
                        else -> rnd.nextBoolean()
                    }
                Pair(EMPTY_COLOR, EMPTY_COLOR) -> // building edges near non-mine targets is pointless
                    when {
                    // first is already ours, pick it => no shuffle
                        activePath.first in ourVertices && activePath.second !in ourVertices -> true
                    // second is already ours, pick it => shuffle
                        activePath.second in ourVertices && activePath.first !in ourVertices -> false
                        else -> rnd.nextBoolean()
                    }
            // first is a mine, start with it => no shuffle
                Pair(MINE_COLOR, EMPTY_COLOR) -> true
            // second is a mine, start with it => shuffle
                else -> false
            }

            val (from, to) = if (noShuffle) {
                activePath.first to activePath.second
            } else {
                activePath.second to activePath.first
            }

            val currentPath = spanningTree
                    .getShortestPath(from, to, ourEdgesFirstPriority)

            if (GlobalSettings.logging) {
                logger.info("Path: $currentPath")
            }

            val selectedPaths = currentPath.isInteresting(
                    spanningTree,
                    this@SpanningTreePunter::EmptyEdgePredicate
            )

            return if (selectedPaths.isNotEmpty()) {
                if (credit >= 0) {

                    // TODO: move to settings
                    if (selectedPaths.first().size / 2 > credit) {
                        return PassMove(me)
                    }

                    credit += 1
                    val selectedPath = selectedPaths
                            .first()
                            .take(credit)

                    credit -= selectedPath.size

                    val selectedVertices = selectedPath.fold(
                            listOf(selectedPath.first().first)
                    ) { a, e -> a + e.second }

                    SplurgeMove(me, selectedVertices)
                } else {
                    val selectedEdge = selectedPaths.first().first()
                    val (from, to) = selectedEdge
                    ClaimMove(me, to, from)
                }
            } else {
                minePairs.removeAt(0)
                sortMinePairs(ourVertices)

                // retry again

                logger.info("Retrying with completed path")

                step(emptyList())
            }
        }
    }

    private fun sortMinePairs(ourVertices: IntSet) {
        // Sort mine pairs by their closeness to our vertices
        val groups = minePairs.groupBy { (from, to) ->
            if (from in ourVertices && to in ourVertices) {
                0 // already connected, but still good enough
            } else if (from in ourVertices) {
                2 // take as is
            } else if (to in ourVertices) {
                1 // should swap
            } else {
                -1 // all the rest
            }
        }

        minePairs = run {
            groups.getOrDefault(2, emptyList()) +
                    groups.getOrDefault(1, emptyList())
                            .map { (from, to) -> to to from } +
                    groups.getOrDefault(0, emptyList()) +
                    groups.getOrDefault(-1, emptyList())
        }.toMutableList()
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

private fun IntSet.pickRandomElementIfNotEmpty(rnd: ThreadLocalRandom?, from: Int, b: Boolean): Int {
    if (size() <= 1) return from
    return pickRandomElement(rnd, from, b)
}

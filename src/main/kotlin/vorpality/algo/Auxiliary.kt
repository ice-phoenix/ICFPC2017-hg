package vorpality.algo

import grph.Grph
import toools.set.IntHashSet
import toools.set.IntSet

fun Collection<Int>.toIntSet() = IntHashSet().apply { this@toIntSet.forEach{ add(it) } }
fun Sequence<Int>.toIntSet() = IntHashSet().apply { this@toIntSet.forEach{ add(it) } }

fun Grph.findPathFromSources(sources: IntSet, destination: Int) =
        sources.filter{ this.containsVertex(it.value) }
                .map { getShortestPath(it.value, destination) }
                .minBy { it.numberOfVertices }!!

fun Grph.edgeVertices(e: Int): Pair<Int, Int> {
    val fst = getOneVertex(e)
    val snd = getTheOtherVertex(e, fst)
    return Pair(fst, snd)
}

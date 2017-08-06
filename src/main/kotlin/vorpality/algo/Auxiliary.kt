package vorpality.algo

import com.carrotsearch.hppc.cursors.IntCursor
import grph.Grph
import toools.set.IntHashSet
import toools.set.IntSet

fun<T> Pair<T, T>.asIterable() = Iterable { object: Iterator<T> {
    var ix = 0
    override fun next(): T = when(ix) { 0 -> first; else -> second }
    override fun hasNext() = ix < 2
} }

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun<T> Any?.uncheckedCast() = this as T

operator fun IntCursor.component1() = value

fun Collection<Int>.toIntSet() = IntHashSet().apply { this@toIntSet.forEach{ add(it) } }
fun Sequence<Int>.toIntSet() = IntHashSet().apply { this@toIntSet.forEach{ add(it) } }

operator fun IntSet.component1(): Int = this.first().value
operator fun IntSet.component2(): Int = this.asSequence().drop(1).first().value

fun Grph.findPathFromSources(sources: IntSet, destination: Int) =
        sources.filter{ this@findPathFromSources.containsVertex(it.value) }
                .map { this@findPathFromSources.getShortestPath(it.value, destination) }
                .minBy { it.numberOfVertices }!!

fun Grph.edgeVertices(e: Int): Pair<Int, Int> {
    val fst = getOneVertex(e)
    val snd = getTheOtherVertex(e, fst)
    return Pair(fst, snd)
}

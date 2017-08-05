package vorpality.algo

import grph.Grph
import grph.algo.search.BFSAlgorithm
import grph.algo.search.GraphSearchListener
import toools.set.IntHashSet
import toools.set.IntSet
import toools.set.IntSets
import vorpality.protocol.Move
import vorpality.protocol.PassMove

class NaiveScoreDrivenPunter: AbstractPunter() {

    val ownedVertices: MutableSet<Int> = mutableSetOf()

    fun calcScore(vertice: Int) {

    }

    override fun step(moves: List<Move>): Move {
        for((_, claim) in moves) if(claim != null) {
            with(claim) {
                if(punter == me) {
                    ownedVertices += source
                    ownedVertices += target
                }
                state.ownerColoring[state.graph.getSomeEdgeConnecting(source, target)] = punter
            }
        }

        val ourEdges = state.ownerColoring.filter { it.value == me }.keys.toIntSet()
        val ownedSubgraph = state.graph.getSubgraphInducedByEdges(ourEdges)

        return PassMove(me) // stub
    }

}
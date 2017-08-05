package vorpality.sim

import vorpality.algo.EMPTY_COLOR
import vorpality.protocol.Map
import vorpality.protocol.Move
import vorpality.protocol.River

data class GraphSim(val map: Map, val owners: MutableMap<River, Int> = mutableMapOf()) {

    init {
        for(river in map.rivers) {
            owners[river.sorted()] = EMPTY_COLOR
        }
    }


    fun handleMove(move: Move) {
        when{
            move.claim != null -> with(move.claim) {
                val river = River(source, target).sorted()
                if(owners[river] == EMPTY_COLOR) owners[River(source, target).sorted()] = punter
            }
        }
    }
}




package vorpality.sim

import vorpality.algo.EMPTY_COLOR
import vorpality.protocol.Map
import vorpality.protocol.Move
import vorpality.protocol.River

data class GraphSim(val map: Map, val owners: MutableMap<River, Int> = mutableMapOf()) {

    init {
        for (river in map.rivers) {
            owners[river.sorted()] = EMPTY_COLOR
        }
    }

    fun handleMove(move: Move) {
        when {
            move.claim != null -> with(move.claim) {
                val river = River(source, target).sorted()
                if (owners[river] == EMPTY_COLOR) owners[river] = punter
            }
            move.splurge != null -> with(move.splurge) {
                var current = route.first()
                for (next in route.drop(1)) {
                    val river = River(current, next).sorted()
                    if (owners[river] == EMPTY_COLOR) owners[river] = punter
                    current = next
                }
            }
        }
    }
}

package vorpality.algo

import vorpality.protocol.ClaimMove
import vorpality.protocol.Move
import vorpality.protocol.River
import vorpality.protocol.SetupData
import vorpality.util.Jsonable
import java.util.concurrent.ThreadLocalRandom

interface Punter {

    fun step(moves: List<Move>, state: Jsonable? = null): Move

    fun setup(setupData: SetupData)

    val currentState: Jsonable

}

class RandomPunter : Punter {

    private class State(data: SetupData) {
        val graph = mutableMapOf<River, Int>()

        init {
            with(data.map) {
                for (river in rivers) {
                    graph.put(river.sorted(), -1)
                }
            }
        }
    }

    var me: Int = -1
    
    private lateinit var state: State

    override fun setup(data: SetupData) {
        me = data.punter
        state = State(data)
    }

    override fun step(moves: List<Move>, oldState: Jsonable?): Move {
        for ((_, claim) in moves) {
            claim ?: continue

            state.graph
                    .put(
                            River(claim.source, claim.target).sorted(),
                            claim.punter
                    )
        }

        val options = state.graph.filter { it.value == -1 }.toList()

        val rnd = ThreadLocalRandom.current().nextInt(0, options.size)

        val river = options[rnd].first

        return ClaimMove(me, river.target, river.source)
    }

    override val currentState: Jsonable
        get() = TODO("not implemented")

}

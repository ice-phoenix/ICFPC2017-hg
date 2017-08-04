package vorpality.algo

import io.vertx.core.json.JsonObject
import vorpality.protocol.ClaimMove
import vorpality.protocol.Move
import vorpality.protocol.River
import vorpality.protocol.SetupData
import vorpality.util.JsonObject
import vorpality.util.Jsonable
import vorpality.util.toJsonable
import java.util.concurrent.ThreadLocalRandom

class RandomPunter : Punter {

    class State(val graph: MutableMap<River, Int>): Jsonable {
        constructor(data: SetupData): this(mutableMapOf()) {
            with(data.map) {
                for (river in rivers) {
                    graph.put(river.sorted(), -1)
                }
            }
        }
    }

    override var me: Int = -1
    private lateinit var state: State

    override fun setup(data: SetupData) {
        me = data.punter
        state = State(data)
    }

    override fun step(moves: List<Move>): Move {
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

        val (source, target) = options[rnd].first

        return ClaimMove(me, target, source)
    }

    override var currentState: JsonObject
        get() = JsonObject("me" to me, "state" to state.toJson())
        set(value) {
            me = value.getInteger("me")
            state = value.getJsonObject("state").toJsonable()
        }

}

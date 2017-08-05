package vorpality.algo

import io.vertx.core.json.JsonObject
import vorpality.protocol.Move
import vorpality.protocol.PassMove
import vorpality.protocol.SetupData

class Passer(override var me: Int = -1, override var currentState: JsonObject = JsonObject()) : Punter {
    override fun step(moves: List<Move>): Move {
        return PassMove(me)
    }

    override fun setup(data: SetupData) {
        me = data.punter
    }

}
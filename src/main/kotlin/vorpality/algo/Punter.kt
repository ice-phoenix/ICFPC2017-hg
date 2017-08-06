package vorpality.algo

import io.vertx.core.json.JsonObject
import vorpality.protocol.Move
import vorpality.protocol.SetupData
import vorpality.util.Jsonable

interface Punter {

    fun step(moves: List<Move>): Move

    fun setup(data: SetupData)

    var currentState: JsonObject

    val me: Int

    val currentScore: Int?

}


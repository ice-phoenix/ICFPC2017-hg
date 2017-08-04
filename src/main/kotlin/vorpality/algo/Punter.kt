package vorpality.algo

import vorpality.protocol.Move
import vorpality.protocol.SetupData
import vorpality.util.Jsonable

interface Punter {

    fun step(moves: List<Move>): Move

    fun setup(data: SetupData)

    val currentState: Jsonable

    val me: Int

}

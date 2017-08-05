package vorpality.protocol

import io.vertx.core.json.JsonObject
import vorpality.util.Jsonable

fun Jsonable.prepare() = toJson().encode()

data class HandshakeRequest(val me: String) : Jsonable
data class HandshakeResponse(val you: String) : Jsonable

typealias PunterId = Int
typealias SiteId = Int

data class Site(val id: SiteId, val x: Double?, val y: Double?) : Jsonable
data class River(val source: SiteId, val target: SiteId) : Jsonable {
    fun sorted() = River(minOf(source, target), maxOf(source, target))
}

data class Future(val source: SiteId, val target: SiteId) : Jsonable

data class Map(val sites: List<Site>, val rivers: List<River>, val mines: List<SiteId>) : Jsonable

data class SetupData(
        val punter: PunterId,
        val punters: Int,
        val map: Map
) : Jsonable

data class Ready(val ready: PunterId, val futures: List<Future>? = null, val state: JsonObject? = null) : Jsonable

data class Move(val pass: Pass? = null, val claim: Claim? = null, val state: JsonObject? = null) : Jsonable {
    // by default our json facility does not throw out nulls
    override fun toJson(): JsonObject =
            super.toJson().apply { removeAll { (_, v) -> v == null } }
}

data class Pass(val punter: PunterId) : Jsonable
data class Claim(val punter: PunterId, val source: SiteId, val target: SiteId) : Jsonable

fun PassMove(punter: PunterId, state: JsonObject? = null) =
        Move(pass = Pass(punter), state = state)

fun ClaimMove(punter: PunterId, source: SiteId, target: SiteId, state: JsonObject? = null) =
        Move(claim = Claim(punter, source, target), state = state)

data class GameTurn(val moves: List<Move>) : Jsonable
data class GameTurnMessage(val move: GameTurn, val state: JsonObject? = null) : Jsonable

data class Score(val punter: PunterId, val score: Int) : Jsonable
data class GameStop(val moves: List<Move>, val scores: List<Score>) : Jsonable
data class GameResult(val stop: GameStop, val state: JsonObject? = null) : Jsonable

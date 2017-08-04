package vorpality.protocol

import io.vertx.core.json.JsonObject
import vorpality.util.Jsonable

data class HandshakeRequest(val me: String): Jsonable
data class HandshakeResponse(val you: String): Jsonable

typealias PunterId = Int
typealias SiteId = Int

data class Site(val id: SiteId): Jsonable
data class River(val source: SiteId, val target: SiteId): Jsonable
data class Map(val sites: List<Site>, val rivers: List<River>, val mines: List<SiteId>): Jsonable

data class SetupData(
        val punter: PunterId,
        val punters: Int,
        val map: Map
): Jsonable

data class Move(val pass: Pass? = null, val claim: Claim? = null): Jsonable {
    // by default our json facility does not throw out nulls
    override fun toJson(): JsonObject =
            super.toJson().apply { removeAll { (_, v) -> v == null } }
}
data class Pass(val punter: PunterId): Jsonable
data class Claim(val punter: PunterId, val source: SiteId, val target: SiteId): Jsonable

fun PassMove(punter: PunterId) = Move(pass = Pass(punter))
fun ClaimMove(punter: PunterId, source: SiteId, target: SiteId) = Move(claim = Claim(punter, source, target))

data class GameTurn(val moves: List<Move>): Jsonable
data class GameTurnMessage(val move: GameTurn): Jsonable

data class Score(val punter: PunterId, val score: Int): Jsonable
data class GameStop(val moves: List<Move>, val scores: List<Score>): Jsonable
data class GameResult(val stop: GameStop): Jsonable

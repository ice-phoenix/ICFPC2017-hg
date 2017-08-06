package vorpality.algo

import io.vertx.core.json.JsonObject
import org.jgrapht.UndirectedGraph
import org.jgrapht.alg.KosarajuStrongConnectivityInspector
import org.jgrapht.alg.StoerWagnerMinimumCut
import org.jgrapht.alg.interfaces.StrongConnectivityAlgorithm
import org.jgrapht.graph.SimpleGraph
import toools.set.IntHashSet
import toools.set.IntSet
import vorpality.protocol.Move
import vorpality.protocol.River
import vorpality.protocol.SetupData

class SpoilerPunter(override var me: Int = -1): Punter {

    override val currentScore: Int?
        get() = null

    override fun setup(data: SetupData) {
        val ug = SimpleGraph{ v0: Int, v1: Int -> River(v0, v1).sorted() }

        for(river in data.map.rivers) {
            ug.addVertex(river.source)
            ug.addVertex(river.target)
            ug.addEdge(river.source, river.target, river)
        }

        val part = StoerWagnerMinimumCut(ug).minCut()

        val cutEdges = ug.edgeSet().filter {
            val s = it.source in part
            val t = it.target in part
            s || t && !(s && t)
        }




    }

    override fun step(moves: List<Move>): Move {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var currentState: JsonObject = JsonObject()
}

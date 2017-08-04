package vorpality.algo

import grph.in_memory.InMemoryGrph
import grph.properties.NumericalProperty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vorpality.protocol.SetupData

abstract class AbstractPunter : Punter {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    protected class State(data: SetupData) {
        val graph = InMemoryGrph()

        val EMPTY_COLOR = -1
        val MINE_COLOR = 1

        val ownerColoring = NumericalProperty(null, 32, EMPTY_COLOR.toLong())
        val mineColoring = NumericalProperty(null, 32, EMPTY_COLOR.toLong())

        init {
            with(data.map) {

                graph.setVerticesColor(mineColoring)
                graph.setEdgesColor(ownerColoring)

                for ((id) in sites) {
                    graph.addVertex(id)
                }
                for ((from, to) in rivers) {
                    graph.addSimpleEdge(from, to, false)
                }
                for (mine in mines) {
                    graph.highlightVertex(mine, MINE_COLOR)
                }
            }
        }
    }

    override var me: Int = -1

    protected lateinit var state: State

    override fun setup(data: SetupData) {
        me = data.punter
        state = State(data)

        logger.info("Graph is: ${state.graph.toGrphText()}")
    }

}

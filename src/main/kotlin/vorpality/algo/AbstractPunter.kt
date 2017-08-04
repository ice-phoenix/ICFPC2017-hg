package vorpality.algo

import grph.in_memory.InMemoryGrph
import grph.properties.NumericalProperty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import toools.gui.TrueColors24Map
import vorpality.protocol.SetupData

abstract class AbstractPunter : Punter {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    protected class State(data: SetupData) {
        val graph = InMemoryGrph()

        val EMPTY_COLOR = 16777215
        val MINE_COLOR = 1

        val ownerColoring = NumericalProperty("owner", 32, EMPTY_COLOR.toLong())
                .apply { palette = TrueColors24Map() }
        val mineColoring = NumericalProperty("mines", 32, EMPTY_COLOR.toLong())
                .apply { palette = TrueColors24Map() }

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

    protected val mines: IntArray
        get() = with(state) {
            mineColoring.findElementsWithValue(
                    MINE_COLOR.toLong(), graph.vertices
            ).toIntArray()
        }

    override fun setup(data: SetupData) {
        me = data.punter
        state = State(data)

        logger.info("Graph is: ${state.graph.toGrphText()}")
    }

}

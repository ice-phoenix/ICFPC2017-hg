package vorpality.algo

import grph.Grph
import grph.in_memory.InMemoryGrph
import grph.io.GrphBinaryReader
import grph.io.GrphBinaryWriter
import grph.properties.NumericalProperty
import io.vertx.core.json.JsonObject
import org.apache.commons.codec.binary.Base64InputStream
import org.apache.commons.codec.binary.Base64OutputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import toools.gui.TrueColors24Map
import vorpality.protocol.SetupData
import vorpality.util.JsonObject
import vorpality.util.Jsonable
import vorpality.util.JsonableCompanion
import vorpality.util.toJsonable
import java.io.ByteArrayOutputStream
import java.io.StringBufferInputStream
import kotlin.reflect.KClass

abstract class AbstractPunter : Punter {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    protected class State(val graph: Grph) : Jsonable {
        val EMPTY_COLOR = 16777215
        val MINE_COLOR = 1

        // TODO: Save/load colorings to/from json

        val ownerColoring = NumericalProperty(null, 32, EMPTY_COLOR.toLong())
                .apply { palette = TrueColors24Map() }
        val mineColoring = NumericalProperty(null, 32, EMPTY_COLOR.toLong())
                .apply { palette = TrueColors24Map() }

        init {
            graph.setVerticesColor(mineColoring)
            graph.setEdgesColor(ownerColoring)
        }

        constructor(data: SetupData) : this(InMemoryGrph()) {
            with(data.map) {
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

        override fun toJson(): JsonObject {
            val bstream = ByteArrayOutputStream()
            GrphBinaryWriter().writeGraph(graph, Base64OutputStream(bstream))
            return JsonObject(
                    "graph" to bstream.toString()
            )
        }

        companion object : JsonableCompanion<State> {
            override val dataklass: KClass<State> = State::class
            override fun fromJson(json: JsonObject): State =
                    GrphBinaryReader()
                            .readGraph(Base64InputStream(StringBufferInputStream(json.getString("graph"))))
                            .let(::State)
        }
    }

    override var me: Int = -1

    override var currentState: JsonObject
        get() = state.toJson().apply { put("me", me) }
        set(value) {
            me = value.getInteger("me")
            state = value.toJsonable<State>()
        }
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

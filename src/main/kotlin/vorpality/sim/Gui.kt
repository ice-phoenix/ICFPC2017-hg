package vorpality.sim

import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.geom.Ellipse2D
import javax.swing.JFrame
import javax.swing.SwingUtilities

class GraphPanel(val graphSim: GraphSim, val me: Int): javax.swing.JPanel() {
    val logger = LoggerFactory.getLogger(javaClass)

    val palette = mapOf(
            0 to Color.GRAY,
            1 to Color.BLUE,
            2 to Color.RED,
            3 to Color.YELLOW,
            4 to Color.MAGENTA,
            5 to Color.CYAN,
            6 to Color.ORANGE,
            7 to Color.DARK_GRAY,
            8 to Color.BLUE.darker().darker(),
            9 to Color.RED.darker().darker(),
            10 to Color.YELLOW.darker().darker(),
            11 to Color.MAGENTA.darker().darker(),
            12 to Color.CYAN.darker().darker(),
            13 to Color.ORANGE.darker().darker(),
            14 to Color.LIGHT_GRAY,
            15 to Color.BLUE.brighter().brighter(),
            16 to Color.RED.brighter().brighter(),
            17 to Color.YELLOW.brighter().brighter(),
            18 to Color.MAGENTA.brighter().brighter(),
            19 to Color.CYAN.brighter().brighter(),
            20 to Color.ORANGE.brighter().brighter()
    )

    fun circle(x: Int, y: Int, radius: Int) =
            Ellipse2D.Double(x.toDouble() - radius.toDouble()/2, y.toDouble() - radius.toDouble()/2, radius.toDouble(), radius.toDouble())

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        background = Color.WHITE

        val g2 = g as Graphics2D
        val map = graphSim.map
        val sites = map.sites.map { it.id to it }.toMap()

        val minX = sites.map { it.value.x!! }.min()!!
        val maxX = sites.map { it.value.x!! }.max()!!
        val minY = sites.map { it.value.y!! }.min()!!
        val maxY = sites.map { it.value.y!! }.max()!!
        val xSpan = (maxX - minX) * 1.1
        val ySpan = (maxY - minY) * 1.1
        val xAdjust = width / xSpan
        val yAdjust = height / ySpan

        fun adjustX(x: Double) = ((x - minX) * xAdjust).toInt() + (WIDTH / 20)
        fun adjustY(y: Double) = ((y - minY) * yAdjust).toInt() + (HEIGHT / 20)

        for(river in map.rivers) {
            g2.stroke = BasicStroke(5.0f)
            val owner = graphSim.owners.getOrDefault(river.sorted(), -1)
            if(owner == me) g2.color = Color.GREEN
            else g2.color = palette.getOrDefault(owner, Color.BLACK)

            g2.drawLine(
                    adjustX(sites[river.source]?.x!!),
                    adjustY(sites[river.source]?.y!!),
                    adjustX(sites[river.target]?.x!!),
                    adjustY(sites[river.target]?.y!!)
            )
        }

        for(site in map.sites) {
            g2.stroke = BasicStroke(8.0f)
            g2.color = Color.DARK_GRAY
            g2.draw(circle(adjustX(site.x!!), adjustY(site.y!!), 5))
        }

        for(mine in map.mines) {
            g2.stroke = BasicStroke(8.0f)
            val site = sites[mine]!!
            g2.color = Color.RED
            g2.draw(circle(adjustX(site.x!!), adjustY(site.y!!), 5))
        }
    }

    private val WIDTH = 640
    private val HEIGHT = 480

    fun showMe() {
        JFrame("graph").apply {
            SwingUtilities.invokeLater {
                preferredSize = Dimension(WIDTH, HEIGHT)
                extendedState = JFrame.MAXIMIZED_BOTH
                contentPane.add(this@GraphPanel)
                this@GraphPanel.repaint()
                pack()
                isVisible = true
            }
            defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        }
    }
}
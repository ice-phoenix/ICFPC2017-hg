package vorpality.sim

import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.geom.Ellipse2D
import javax.swing.JFrame
import javax.swing.SwingUtilities
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D

import vorpality.algo.Punter
import com.sun.awt.SecurityWarning.getSize
import java.awt.font.TextAttribute
import sun.font.FontFamily.getFamily
import java.util.HashMap



private val palette = mapOf(
        0 to Color.RED,
        1 to Color.BLUE,
        2 to Color.ORANGE,
        3 to Color.YELLOW,
        4 to Color.MAGENTA,
        5 to Color.CYAN,
        6 to Color.GRAY,
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

class GraphPanel(val graphSim: GraphSim, val punter: Punter, val punters: Int): javax.swing.JPanel() {
    val logger = LoggerFactory.getLogger(javaClass)

    fun circle(x: Int, y: Int, radius: Int) =
            Ellipse2D.Double(x.toDouble() - radius.toDouble()/2, y.toDouble() - radius.toDouble()/2, radius.toDouble(), radius.toDouble())

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val legendHeight = 25

        background = Color.WHITE

        val g2 = g as Graphics2D
        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        )
        val map = graphSim.map
        val sites = map.sites.map { it.id to it }.toMap()

        val minX = sites.map { it.value.x!! }.min()!!
        val maxX = sites.map { it.value.x!! }.max()!!
        val minY = sites.map { it.value.y!! }.min()!!
        val maxY = sites.map { it.value.y!! }.max()!!
        val xSpan = (maxX - minX) * 1.1
        val ySpan = (maxY - minY) * 1.1
        val xAdjust = width / xSpan
        val yAdjust = (height - legendHeight) / ySpan

        fun adjustX(x: Double) = ((x - minX) * xAdjust).toInt() + (width / 20)
        fun adjustY(y: Double) = ((y - minY) * yAdjust).toInt() + ((height - legendHeight) / 20)

        for(river in map.rivers) {
            g2.stroke = BasicStroke(5.0f)
            val owner = graphSim.owners.getOrDefault(river.sorted(), -1)
            if(owner == punter.me) g2.color = Color.GREEN
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

        run { // Legend
            val cellWidth = width.toFloat() / punters

            (0 until punters).forEach {
                g2.color = if(it == punter.me) Color.GREEN else palette[it]
                g2.fill(Rectangle2D.Float(it * cellWidth, height.toFloat() - legendHeight, cellWidth, legendHeight.toFloat()))
                g2.color = Color.BLACK
                g2.drawString("${it}", it * cellWidth + cellWidth/2, height.toFloat() - legendHeight - 2)
            }
        }

        punter.currentScore?.let {
            g2.color = Color.BLACK
            val attributes = HashMap<TextAttribute, Any>()

            attributes.put(TextAttribute.FAMILY, g2.font.getFamily())
            attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD)
            attributes.put(TextAttribute.SIZE, (g2.font.getSize() * 1.4).toInt())
            g2.font = Font.getFont(attributes)

            g2.drawString("${punter.currentScore}", 5.0f, height.toFloat() - legendHeight - 20)
        }

//        for(river in map.rivers) {
//            g2.stroke = BasicStroke(0.3f)
//            g2.color = Color.WHITE
//            g2.drawLine(
//                    adjustX(sites[river.source]?.x!!),
//                    adjustY(sites[river.source]?.y!!),
//                    adjustX(sites[river.target]?.x!!),
//                    adjustY(sites[river.target]?.y!!)
//            )
//        }
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
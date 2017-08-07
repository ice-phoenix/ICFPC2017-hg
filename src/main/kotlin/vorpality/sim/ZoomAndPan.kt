package vorpality.sim

import java.awt.geom.AffineTransform
import sun.awt.windows.ThemeReader.getPoint
import java.awt.Component
import java.awt.Point
import java.awt.event.*
import java.awt.geom.NoninvertibleTransformException
import java.awt.geom.Point2D


class ZoomAndPanListener : MouseListener, MouseMotionListener, MouseWheelListener {

    private var targetComponent: Component? = null

    var zoomLevel = 0
    private var minZoomLevel = DEFAULT_MIN_ZOOM_LEVEL
    private var maxZoomLevel = DEFAULT_MAX_ZOOM_LEVEL
    private var zoomMultiplicationFactor = DEFAULT_ZOOM_MULTIPLICATION_FACTOR

    private var dragStartScreen: Point? = null
    private var dragEndScreen: Point? = null
    var coordTransform = AffineTransform()

    constructor(targetComponent: Component) {
        this.targetComponent = targetComponent
    }

    constructor(targetComponent: Component, minZoomLevel: Int, maxZoomLevel: Int, zoomMultiplicationFactor: Double) {
        this.targetComponent = targetComponent
        this.minZoomLevel = minZoomLevel
        this.maxZoomLevel = maxZoomLevel
        this.zoomMultiplicationFactor = zoomMultiplicationFactor
    }


    override fun mouseClicked(e: MouseEvent) {}

    override fun mousePressed(e: MouseEvent) {
        dragStartScreen = e.getPoint()
        dragEndScreen = null
    }

    override fun mouseReleased(e: MouseEvent) {
        //        moveCamera(e);
    }

    override fun mouseEntered(e: MouseEvent) {}

    override fun mouseExited(e: MouseEvent) {}

    override fun mouseMoved(e: MouseEvent) {}

    override fun mouseDragged(e: MouseEvent) {
        moveCamera(e)
    }

    override fun mouseWheelMoved(e: MouseWheelEvent) {
        //        System.out.println("============= Zoom camera ============");
        zoomCamera(e)
    }

    private fun moveCamera(e: MouseEvent) {
        //        System.out.println("============= Move camera ============");
        try {
            dragEndScreen = e.point
            val dragStart = transformPoint(dragStartScreen!!)
            val dragEnd = transformPoint(dragEndScreen!!)
            val dx = dragEnd.getX() - dragStart.getX()
            val dy = dragEnd.getY() - dragStart.getY()
            coordTransform.translate(dx, dy)
            dragStartScreen = dragEndScreen
            dragEndScreen = null
            targetComponent!!.repaint()
        } catch (ex: NoninvertibleTransformException) {
            ex.printStackTrace()
        }

    }

    private fun zoomCamera(e: MouseWheelEvent) {
        try {
            val wheelRotation = e.wheelRotation
            val p = e.point
            if (wheelRotation > 0) {
                if (zoomLevel < maxZoomLevel) {
                    zoomLevel++
                    val p1 = transformPoint(p)
                    coordTransform.scale(1 / zoomMultiplicationFactor, 1 / zoomMultiplicationFactor)
                    val p2 = transformPoint(p)
                    coordTransform.translate(p2.getX() - p1.getX(), p2.getY() - p1.getY())
                    targetComponent!!.repaint()
                }
            } else {
                if (zoomLevel > minZoomLevel) {
                    zoomLevel--
                    val p1 = transformPoint(p)
                    coordTransform.scale(zoomMultiplicationFactor, zoomMultiplicationFactor)
                    val p2 = transformPoint(p)
                    coordTransform.translate(p2.getX() - p1.getX(), p2.getY() - p1.getY())
                    targetComponent!!.repaint()
                }
            }
        } catch (ex: NoninvertibleTransformException) {
            ex.printStackTrace()
        }

    }

    @Throws(NoninvertibleTransformException::class)
    private fun transformPoint(p1: Point): Point2D.Float {
        //        System.out.println("Model -> Screen Transformation:");
        //        showMatrix(coordTransform);
        val inverse = coordTransform.createInverse()
        //        System.out.println("Screen -> Model Transformation:");
        //        showMatrix(inverse);

        val p2 = Point2D.Float()
        inverse.transform(p1, p2)
        return p2
    }

    private fun showMatrix(at: AffineTransform) {
        val matrix = DoubleArray(6)
        at.getMatrix(matrix)  // { m00 m10 m01 m11 m02 m12 }
        val loRow = arrayOf(0, 0, 1)
        for (i in 0..1) {
            print("[ ")
            var j = i
            while (j < matrix.size) {
                System.out.printf("%5.1f ", matrix[j])
                j += 2
            }
            print("]\n")
        }
        print("[ ")
        for (i in loRow.indices) {
            System.out.printf("%3d   ", *loRow)

        }

        print("]\n")

        println("---------------------")

    }

    companion object {
        val DEFAULT_MIN_ZOOM_LEVEL = -20
        val DEFAULT_MAX_ZOOM_LEVEL = 10
        val DEFAULT_ZOOM_MULTIPLICATION_FACTOR = 1.2
    }

}
package vorpality

import grph.Grph
import grph.algo.topology.ClassicalGraphs
import org.junit.Test

class GrphSntyChck {
    @Test
    fun wrtRdGrphTst() {
        val g = ClassicalGraphs.completeBipartiteGraph(2, 2)
        val s = g.toGrphText()
        // System.out.println(s);
        val h = Grph.fromGrphText(s)
    }
}

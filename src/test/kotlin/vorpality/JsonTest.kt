package vorpality

import io.vertx.core.json.JsonObject
import org.junit.Test
import vorpality.protocol.Claim
import vorpality.protocol.Move
import vorpality.protocol.PassMove
import vorpality.util.Jsonable
import vorpality.util.toJsonable
import kotlin.test.assertEquals

data class Example(val s: List<String?>): Jsonable
data class OtherExample(val inner: Example): Jsonable

class JsonTest {
    @Test
    fun `test that toJson() works like a charm and stuff X-)`() {

        assertEquals(
                JsonObject(//language=json
                        """
    {
        "s" : ["Hello", "world", "!"]
    }
"""
                ),
                Example(listOf("Hello", "world", "!")).toJson()
        )

        assertEquals(
                JsonObject(//language=json
                        """ {
    "inner" : {
        "s" : ["Hello", "world", "!"]
    }
}
"""
                ),
                OtherExample(Example(listOf("Hello", "world", "!"))).toJson()
        )

    }


    @Test
    fun `test that fromJson() works like a charm and stuff X-)`() {

        assertEquals(
                JsonObject(//language=json
                        """
    {
        "s" : ["Hello", "world", "!"]
    }
"""
                ).toJsonable(),
                Example(listOf("Hello", "world", "!"))
        )

        assertEquals(
                JsonObject(//language=json
                        """ {
    "inner" : {
        "s" : ["Hello", "world", "!"]
    }
}
"""
                ).toJsonable(),
                OtherExample(Example(listOf("Hello", "world", "!")))
        )

    }

    @Test
    fun `check that non-trivial cases work`() {
        val m = PassMove(2)

        assertEquals(JsonObject(//language=json
                """
{
  "pass": { "punter": 2 }
}
        """), m.toJson())

        val m2 = JsonObject(//language=json
                """
{
    "claim": { "punter": 3, "source": 1, "target": 2 }
}
""")

        assertEquals(Move(claim = Claim(3, 1, 2)), m2.toJsonable<Move>())

    }
}
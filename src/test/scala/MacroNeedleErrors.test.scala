/** Compile-time error paths for `worldofregex.macros.Needle.compile`.
  *
  * Uses `scala.compiletime.testing.typeCheckErrors` to run the macro inside
  * the typer and assert on the emitted error messages without making the
  * test source itself fail to compile.
  */

package tests

import scala.compiletime.testing.{typeCheckErrors, Error}

class MacroNeedleErrors extends munit.FunSuite {

    test("non-literal argument is rejected at compile time") {
        val errors = typeCheckErrors(
            """
            val s: String = "a"
            worldofregex.macros.Needle.compile(s)
            """
        )
        assert(errors.nonEmpty, "expected at least one compile error")
        assert(
            errors.exists(_.message.contains("compile-time constant")),
            s"expected error mentioning 'compile-time constant', got: ${errors.map(_.message)}"
        )
    }

    test("unparseable regex literal is rejected at compile time") {
        val errors = typeCheckErrors("""worldofregex.macros.Needle.compile("[")""")
        assert(errors.nonEmpty, "expected at least one compile error")
        assert(
            errors.exists(_.message.contains("failed to compile regex")),
            s"expected error mentioning 'failed to compile regex', got: ${errors.map(_.message)}"
        )
    }

    test("valid literal still type-checks (control)") {
        val errors = typeCheckErrors("""worldofregex.macros.Needle.compile("a+b")""")
        assertEquals(errors, List.empty[Error], "valid literal should not produce compile errors")
    }
}

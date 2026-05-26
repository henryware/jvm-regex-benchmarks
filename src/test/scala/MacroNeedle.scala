/** Test-scope adapter that exposes the compile-time `worldofregex.macros.Needle`
  * macro through the project's RegexEngine interface so it can ride the
  * Sanity test matrix alongside the runtime Needle engine.
  *
  * The macro accepts only literal patterns, so we maintain a hand-curated
  * map from every raw pattern Sanity feeds this engine to its macro-compiled
  * Pattern (mirroring the runtime Needle's manglePattern rewrites).  An
  * unknown pattern fails loudly so the bake list and Sanity's test data
  * cannot silently drift apart.
  */

package tests

import worldofregex.{Needle => RuntimeNeedle, Regex, RegexEngine, RegexException, LibraryVersion}
import worldofregex.macros.{Needle => MNeedle}
import com.justinblank.strings.Pattern

object MacroNeedle extends RegexEngine {
    val name = "MacroNeedle"
    val version = LibraryVersion.fromClass(classOf[com.justinblank.strings.DFACompiler])

    /* Keys are the raw patterns Sanity passes to compile(); values are
     * macro-compiled at *test-compile* time from the mangled form, which
     * is what RuntimeNeedle would feed DFACompiler at runtime. */
    private val baked: Map[String, Pattern] = Map(
        // consensusTestCases
        "a|b"     -> MNeedle.compile("a|b"),
        "a+b*"    -> MNeedle.compile("a+b*"),
        "a?b+"    -> MNeedle.compile("a?b+"),
        "a{2,3}"  -> MNeedle.compile("a{2,3}"),
        "(a)(b)"  -> MNeedle.compile("(a)(b)"),
        "[0-9]+"  -> MNeedle.compile("[0-9]+"),
        "[^a-z]+" -> MNeedle.compile("[^a-z]+"),
        "xyz"     -> MNeedle.compile("xyz"),
        "\\."     -> MNeedle.compile("\\."),
        "a*"      -> MNeedle.compile("a*"),
        "aba|b"   -> MNeedle.compile("aba|b"),
        // perlTestCases
        "a|b|ab"  -> MNeedle.compile("a|b|ab"),
        // utfTestCases
        "😂"       -> MNeedle.compile("😂"),
        ".{12}"   -> MNeedle.compile(".{12}"),
        "à*"      -> MNeedle.compile("à*"),
        "é*"      -> MNeedle.compile("é*"),
        // dotMatchesNewline probe
        "."       -> MNeedle.compile("."),
        // propertyPatterns
        "[A-Z][a-z]{1,11}"                -> MNeedle.compile("[A-Z][a-z]{1,11}"),
        ".{1,12}"                          -> MNeedle.compile(".{1,12}"),
        "\\d{3}-\\d{3}-\\d{4}"            -> MNeedle.compile("[0-9]{3}-[0-9]{3}-[0-9]{4}"),
        "(?:c*ac*ac*b)*(?:c*ac*ac*ac*b)"  -> MNeedle.compile("(c*ac*ac*b)*(c*ac*ac*ac*b)")
    )

    def compile(pattern: String): Regex = {
        val p = baked.getOrElse(
            pattern,
            throw new RegexException(
                s"MacroNeedle: pattern not in bake list: /$pattern/. " +
                "Add an entry to tests.MacroNeedle.baked to cover this Sanity case."
            )
        )
        RuntimeNeedle.regexFromPattern(p, pattern, name)
    }
}

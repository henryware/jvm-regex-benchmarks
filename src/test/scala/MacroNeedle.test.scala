/** Layer 2 tests for `worldofregex.macros.Needle.compile` — direct
  * equivalence with runtime `DFACompiler.compile` plus infrastructure
  * checks (cache, distinct-literal hashing, multi-chunk reassembly).
  */

package tests

import com.justinblank.strings.{DFACompiler, Pattern}
import worldofregex.macros.{Needle => MNeedle, NeedleRuntime}

import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

class MacroNeedleSpec extends munit.FunSuite {

    private val counter = new AtomicLong(0L)
    private def runtimePattern(regex: String): Pattern = {
        val cls = s"MacroNeedleSpecRuntime_${counter.incrementAndGet()}"
        DFACompiler.compile(regex, cls)
    }

    private def checkEquivalence(label: String, mp: Pattern, regex: String, texts: Seq[String]): Unit = {
        val rp = runtimePattern(regex)
        for (txt <- texts) {
            val rm = rp.matcher(txt)
            val mm = mp.matcher(txt)
            assertEquals(mm.matches(), rm.matches(), s"matches() differs for $label on '$txt'")
            assertEquals(mm.containedIn(), rp.matcher(txt).containedIn(),
                s"containedIn() differs for $label on '$txt'")
            val rFind = rp.matcher(txt).find()
            val mFind = mp.matcher(txt).find()
            assertEquals(mFind.matched, rFind.matched, s"find().matched differs for $label on '$txt'")
            if (rFind.matched) {
                assertEquals(mFind.start, rFind.start, s"find().start differs for $label on '$txt'")
                assertEquals(mFind.`end`, rFind.`end`, s"find().end differs for $label on '$txt'")
            }
        }
    }

    private val texts = Seq("", "a", "ab", "abc", "xyz", "aabbcc", "AaBbCc123", "  ", "a.b")

    test("equivalence: literal 'a'") {
        checkEquivalence("a", MNeedle.compile("a"), "a", texts)
    }

    test("equivalence: literal 'abc'") {
        checkEquivalence("abc", MNeedle.compile("abc"), "abc", texts)
    }

    test("equivalence: alternation 'a|b'") {
        checkEquivalence("a|b", MNeedle.compile("a|b"), "a|b", texts)
    }

    test("equivalence: alternation 'foo|bar|baz'") {
        checkEquivalence("foo|bar|baz", MNeedle.compile("foo|bar|baz"), "foo|bar|baz",
            texts ++ Seq("foo", "bar", "baz", "foobar", "qux"))
    }

    test("equivalence: char class '[a-z]+'") {
        checkEquivalence("[a-z]+", MNeedle.compile("[a-z]+"), "[a-z]+", texts)
    }

    test("equivalence: char class '[0-9]+'") {
        checkEquivalence("[0-9]+", MNeedle.compile("[0-9]+"), "[0-9]+",
            texts ++ Seq("123", "a1b2", "999999"))
    }

    test("equivalence: negated class '[^a-z]'") {
        checkEquivalence("[^a-z]", MNeedle.compile("[^a-z]"), "[^a-z]", texts)
    }

    test("equivalence: quantifiers 'a?b*c+'") {
        checkEquivalence("a?b*c+", MNeedle.compile("a?b*c+"), "a?b*c+",
            texts ++ Seq("c", "bc", "abc", "abbbcc", "ac"))
    }

    test("equivalence: bounded 'a{2,3}'") {
        checkEquivalence("a{2,3}", MNeedle.compile("a{2,3}"), "a{2,3}",
            texts ++ Seq("a", "aa", "aaa", "aaaa", "aaaaa"))
    }

    test("equivalence: groups '(a)(b)'") {
        checkEquivalence("(a)(b)", MNeedle.compile("(a)(b)"), "(a)(b)", texts)
    }

    test("equivalence: escaped dot '\\.'") {
        checkEquivalence("\\.", MNeedle.compile("\\."), "\\.", texts ++ Seq(".", "a.b"))
    }

    test("equivalence: star 'a*'") {
        checkEquivalence("a*", MNeedle.compile("a*"), "a*", texts)
    }

    test("equivalence: dot '.'") {
        checkEquivalence(".", MNeedle.compile("."), ".", texts ++ Seq("\n"))
    }

    test("equivalence: '(a|b)*'") {
        checkEquivalence("(a|b)*", MNeedle.compile("(a|b)*"), "(a|b)*",
            texts ++ Seq("ababab", "aaaa", "bbbb", "abab", "c"))
    }

    test("equivalence: bounded any '.{1,5}'") {
        checkEquivalence(".{1,5}", MNeedle.compile(".{1,5}"), ".{1,5}",
            texts ++ Seq("xx", "xxxxx", "xxxxxx"))
    }

    /* ---- Infrastructure: cache, distinct hashing, multi-chunk reassembly ---- */

    test("cache hit: same literal yields same Pattern instance") {
        val p1 = MNeedle.compile("a+b")
        val p2 = MNeedle.compile("a+b")
        assert(p1 eq p2, s"expected cache hit for repeated literal, got distinct instances $p1 vs $p2")
    }

    test("distinct literals yield distinct Pattern instances") {
        val pA = MNeedle.compile("a+")
        val pB = MNeedle.compile("b+")
        assert(!(pA eq pB), "different regex literals must produce different Pattern instances")
    }

    test("NeedleRuntime.materialize joins multiple chunks") {
        val cls = "MacroNeedleSpec$ManualChunk$"
        val bytes = DFACompiler.compileToBytes("xy", cls)
        val b64 = Base64.getEncoder.encodeToString(bytes)
        val third = math.max(1, b64.length / 3)
        val c1 = b64.substring(0, third)
        val c2 = b64.substring(third, third * 2)
        val c3 = b64.substring(third * 2)
        assert(c1.nonEmpty && c2.nonEmpty && c3.nonEmpty, "test setup: chunks must be non-empty")
        val materialized = NeedleRuntime.materialize(cls, c1, c2, c3)
        assert(materialized.matcher("xy").matches(), "multi-chunk-materialized pattern should match 'xy'")
        assert(!materialized.matcher("xz").matches(), "multi-chunk-materialized pattern should not match 'xz'")
    }
}

/** Sanity checks of the Regex Engine implementations
  */

package tests

import munit.ScalaCheckSuite
import org.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.Prop.{forAll,propBoolean}
import org.scalacheck.Arbitrary.arbitrary 
import scala.collection.mutable

import wolfendale.scalacheck.regexp.RegexpGen

import worldofregex.*

class Sanity extends ScalaCheckSuite {

    case class TestCase(pattern: String, text: String, whole: String, partial: String, first: String, all: String)
    case class SubmatchCase(pattern:String, text:String, expected:List[String]);

    def checkRegexBehavior(
        baseName: String,
        rx: Regex,
        tc: TestCase
    )(implicit loc: munit.Location): Unit = {
        test(baseName + "wholeMatch") {
            val obtained = rx.hasWholeMatch(tc.text).toString
            assertEquals(
                obtained,
                tc.whole,
                s"Whole match failed for pattern '${rx}' on text '${tc.text}'\nExpected: ${tc.whole}, Got: $obtained"
            )
        }
        test(baseName + "partialMatch") {
            val obtained = rx.hasPartialMatch(tc.text).toString
            assertEquals(
                obtained,
                tc.partial,
                s"Partial match failed for pattern '${rx}' on text '${tc.text}'\nExpected: ${tc.partial}, Got: $obtained"
            )
        }
        test(baseName + "locateFirst") {
            val obtained = rx.locateFirstMatchIn(tc.text).toString
            assertEquals(
                obtained,
                tc.first,
                s"First match failed for pattern '${rx}' on text '${tc.text}'\nExpected: ${tc.first}, Got: $obtained\n" +
                s"Note: 'None' means no match found, 'Some((x,y))' shows match boundaries"
            )
        }
        test(baseName + "locateAll") {
            val obtained = rx.locateAllMatchIn(tc.text).mkString(",")
            assertEquals(
                obtained,
                tc.all,
                s"All matches failed for pattern '${rx}' on text '${tc.text}'\nExpected: ${tc.all}, Got: $obtained\n" +
                s"Note: Comma-separated list of (start,end) positions"
            )
        }
    }


    def submatchTest(
        name: String,
        rx:Regex,
        sc: SubmatchCase
    )(implicit loc: munit.Location): Unit = {
        test(name + "submatches") {
            val obtained = rx.locateAllMatchIn(sc.text).map{
                _.subregions.map{case (s,e) => s"($s,$e)"}.mkString(",")
            }.toList
            assertEquals(
                obtained,
                sc.expected, 
                s"Submatches failed for pattern '${rx}' on text '${sc.text}'\nExpected: ${sc.expected}, Got: $obtained"
            )
        }
    }

    def checkWhole[T](
        name: String,
        pat:String,
        rx:Regex)={
        property(name+"CheckWhole") {
            forAll(RegexpGen.from(pat)){txt => 
                val obtained = rx.hasWholeMatch(txt);
                assertEquals(obtained,true)
            }
        }
    }



    val consensusTestCases = List(
        /* No anchors here as our DFAs don't support them.
         * Also, no cases where POSIX vs Perl matters
         */

        // Basic alternation and locating
        TestCase("a|b", "ab", whole="false", partial="true", first="Some((0,1))", all="(0,1),(1,2)"),

        // Quantifiers
        TestCase("a+b*", "aaabbc", whole="false", partial="true", first="Some((0,5))", all="(0,5)"), 
        TestCase("a?b+", "abb", whole="true", partial="true", first="Some((0,3))", all="(0,3)"),
        TestCase("a?b+", "bb", whole="true", partial="true", first="Some((0,2))", all="(0,2)"),
        TestCase("a{2,3}", "aaaa", whole="false", partial="true", first="Some((0,3))", all="(0,3)"), 
        // Groups
        TestCase("(a)(b)", "ab", whole="true", partial="true", first="Some((0,2))", all="(0,2)"),
        // Character Classes
        TestCase("[0-9]+", "abc123def45", whole="false", partial="true", first="Some((3,6))", all="(3,6),(9,11)"),
        TestCase("[^a-z]+", "ABC123def", whole="false", partial="true", first="Some((0,6))", all="(0,6)"), 
        // No Match
        TestCase("xyz", "abc", whole="false", partial="false", first="None", all=""),
        // Escaped characters
        TestCase("\\.", "a.b", whole="false", partial="true", first="Some((1,2))", all="(1,2)"),
        // Empty Match   
        TestCase("a*", "bbb", whole="false", partial="true", first="Some((0,0))", all="(0,0),(1,1),(2,2),(3,3)"),
        TestCase("a*", "", whole="true", partial="true", first="Some((0,0))", all="(0,0)"),
        TestCase("aba|b", "abab", whole="false", partial="true", first="Some((0,3))", all="(0,3),(3,4)")

    )

    /* These are tests of high CodePoints.  UTF-8 is in play because of
     * Joni, but everybody needs to work with UTF-16.
     *
     *  broken out because KMY doesn't support surrogate pairs for the
     *  obvious reason that Java did have them back then.
     */
    val utfTestCases = List(
        TestCase("😂", "éa😂a😂a𝄞€4é", whole="false",partial="true",first="Some((2,4))",all="(2,4),(5,7)"),
        TestCase(".{12}","땊╎㮯㉛뢣둻惐肌U샿⻖",whole="true",partial="true",first="Some((0,12))",all="(0,12)"),
        TestCase("à*" ,"éé", whole="false", partial="true", first="Some((0,0))", all="(0,0),(1,1),(2,2)"),
        TestCase("é*" ,"€€", whole="false", partial="true", first="Some((0,0))", all="(0,0),(1,1),(2,2)"),

        // not sure I believe this is the Right Thing: I think it
        // should advance by CodePoint.  Still, its the consensus.
        TestCase("a*" ,"😂😂", whole="false", partial="true", first="Some((0,0))", all="(0,0),(1,1),(2,2),(3,3),(4,4)")
    )

    /* These are what the Posix style regex engines should give */
    val posixTestCases = List(
        TestCase("a|ab", "ab", whole="true", partial="true", first="Some((0,2))", all="(0,2)")
    )

    val perlTestCases = List(
        // Basic alternation and locating
        TestCase("a|b|ab", "ab", whole="true", partial="true", first="Some((0,1))", all="(0,1),(1,2)")
    )

    val anchorTestCases = List(
        // Basic alternation and locating
        TestCase("a|b|ab", "ab", whole="true", partial="true", first="Some((0,1))", all="(0,1),(1,2)"), // should find whole match if it has to
        // Anchors - test start/end of string matching
        TestCase("^a.c$", "abc", whole="true", partial="true", first="Some((0,3))", all="(0,3)"), // Should match entire string
        TestCase("^a.c$", "abX", whole="false", partial="false", first="None", all=""),  // Wrong ending character
        TestCase("c$", "abc", whole="false", partial="true", first="Some((2,3))", all="(2,3)"), // End anchor with partial match
        TestCase("a*$", "abc", whole="false", partial="true", first="Some((3,3))", all="(3,3)") // End anchor with partial match
    )



    val submatchCases = List(
        SubmatchCase("(a)(b)", "ab", List("(0,1),(1,2)")),
        SubmatchCase("(a(b))", "ab", List("(0,2),(1,2)")),
        SubmatchCase("(a|(b))c", "ac", List("(0,1),(-1,-1)")),
        SubmatchCase("(a*)(b+)", "aaabbb", List("(0,3),(3,6)")),
        SubmatchCase("(?:a)(b)", "ab", List("(1,2)")),
        SubmatchCase("(a)(?:b)", "ab", List("(0,1)")),
        SubmatchCase("([0-9]{3})-([0-9]{3})", "650-253", List("(0,3),(4,7)")),
        SubmatchCase("(a(.?))", "ab", List("(0,2),(1,2)")),
        SubmatchCase("(a(.?))", "aba", List("(0,2),(1,2)","(2,3),(3,3)")),
        SubmatchCase("(😂)", "éa😂a😂a𝄞€4é", List("(2,4)","(5,7)")),
        SubmatchCase("a(𝄞)", "éa😂a😂a𝄞€4é", List("(8,10)")),
        SubmatchCase("(€)4", "éa😂a😂a𝄞€4é", List("(10,11)")),
        SubmatchCase("(é)a", "éa😂a😂a𝄞€4é", List("(0,1)")),
        SubmatchCase("(é)", "éa😂a😂a𝄞€4é", List("(0,1)","(12,13)"))
    )

    val posixEngines = Set("MonqJFA", "BricsScreen", "DkBrics", "BricsWalk")
    val anchoredEngines= Set("Joni", "JavaUtil", "Re2J", "Florian")
    val submatchEngines = Set("Joni", "JavaUtil", "Re2J", "Florian", "HarpoNFA", "HarpoInterp")
    val noSurrogatePairEngines = Set("KMY")
    val dotMatchesNewlineEngines = posixEngines

    // Generate tests for each engine and test case
    for (engine <- ENGINES) {
        val engineName = engine.name

        val activeTestCases = consensusTestCases ++
            (if (posixEngines.contains(engineName)) posixTestCases else perlTestCases) ++
            (if (anchoredEngines.contains(engineName)) anchorTestCases else Nil)  ++
            (if (noSurrogatePairEngines.contains(engineName)) Nil else utfTestCases) :+
            (if (dotMatchesNewlineEngines.contains(engineName))
                    TestCase(".", "\n", whole="true", partial="true", first="Some((0,1))", all="(0,1)")
                else
                    TestCase(".", "\n", whole="false", partial="false", first="None", all=""))

        val activeSubmatchCases = if (submatchEngines.contains(engineName)) submatchCases else Nil


        // Run specific functionality tests
        for (tc <- activeTestCases) {
            checkRegexBehavior(s"$engineName-${tc.pattern}-", engine.compile(tc.pattern), tc)
        }

        // Run submatch tests for supported engines
        for (sc <- activeSubmatchCases) {
            submatchTest(s"$engineName-${sc.pattern}-", engine.compile(sc.pattern), sc)
        }

        // Run property-based tests for whole matches
        val propertyPatterns = List("[A-Z][a-z]{1,11}", """.{1,12}""", "\\d{3}-\\d{3}-\\d{4}",
                                    """(?:c*ac*ac*b)*(?:c*ac*ac*ac*b)""" // seven c's pattern
        )
        for (pattern <- propertyPatterns) {
            checkWhole(s"$engineName-$pattern-", pattern, engine.compile(pattern))
        }
    }
}

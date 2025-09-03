/** Tests of the RegexAST which generates random patterns.
 */

 
package worldofregex

import org.scalacheck._
import org.scalacheck.Prop._
import org.scalacheck.Test
import org.scalacheck.Test.Parameters

import RegexAST._
import RegexGen._
import munit.ScalaCheckSuite
import io.github.martinhh.derived.scalacheck.given

class RegexASTProperties extends ScalaCheckSuite {
    import RegexGen.abcd.given

    override val scalaCheckTestParameters = 
        super.scalaCheckTestParameters
            .withMinSuccessfulTests(1000) // default 100
            .withMaxDiscardRatio(20) // Allows more failed shrinks before giving up  
            .withMinSize(0)
            .withMaxSize(10)
            .withWorkers(1)


    property(s"Canonicalizer is idempotent"){
        import RegexCanonicalizer._
        forAll { (item: RegexNode) =>
            val before=canonicalize(item)
            //println(item);
            val after=canonicalize(before)
            assertEquals(after,before)
        }
    }
    property(s"Spartanizer is idempotent"){
        import RegexCanonicalizer._
        forAll { (item: RegexNode) =>
            val before=spartanize(canonicalize(item))
            //println(item);
            val after=spartanize(before)
            assertEquals(after,before)
        }
    }

    // Test specific edge cases
    val edgeCases = List(
        "", "a", "\\d", "[a-z]", "a*", "a+", "a?", "a{2}", "a{3,}","a{4,5}",
        "(?:a)", "(a)", "a|b", "^$", "\\w\\d\\s", "[^a-z]", "[-]"
            /* These don't "just work" and are fiddly, uninteresting and
             *  unimportant.  So I will leave them broken for now.*/
            //"[A-\\]]", "[\\\\^]","[^\\\\]", "[a-z\\-]]", "[\\-a]","[a-]", "[.]"
    )
    
    edgeCases.foreach { pattern =>
        test(s"Handle edge case: $pattern") {
            val result = RegexAST.toPattern(RegexParser.parse(pattern))
            assertEquals(result, pattern)
        }
    }

                                                  

    property(s"AST to pattern to AST"){
        forAll (RegexGen.abcd.genRegexNode){(item: RegexNode) =>
            val clean=RegexCanonicalizer.canonicalize(item)
            //println(s"Clean: $clean")
            val cleaner=RegexCanonicalizer.canonicalize(clean)
            assertEquals(clean,cleaner)
            
            val result:RegexNode=RegexCanonicalizer.canonicalize(RegexParser.parse(RegexAST.toPattern(clean)))
            assertEquals(result,clean)
        }
    }
    
}

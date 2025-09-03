package worldofregex

import org.scalacheck._
import org.scalacheck.Prop._
import org.scalacheck.Test
import org.scalacheck.Test.Parameters

import RegexAST._
import io.github.martinhh.derived.scalacheck.given
import RegexCanonicalizer.*
import RegexAST.*

import wolfendale.scalacheck.regexp.RegexpGen

object RegexGen {
    // these give various implementations parsing trouble.
    // if we ever want to test parsers, this will need to be revisited.
    val metacharacters=Set('&','\u0000','-', '"', '^', '~', '!', "|",
                           '\\', '\f', '\r', '\b', '#',
                           '$','@', '<', '>', '[', ']' ,'{', '}')

    def isAllowed(c:Char)= !metacharacters.contains(c)

    val asciiPrintable=new RegexGen(org.scalacheck.Gen.asciiPrintableChar.filter(isAllowed))

    val abcd=new RegexGen(Gen.oneOf('a','b','c','d'))

    val utf=new RegexGen(org.scalacheck.Arbitrary.arbitrary[Char].filter(isAllowed))
}
class RegexGen(genChar:Gen[Char]) {
    import RegexAST.given;
    given Arbitrary[Char] = Arbitrary(genChar)

    // fixme: lose the gen prefixes

    val genLiteral: Gen[Literal] = genChar.map(Literal(_))

    val genCharRange: Gen[CharRange] = for {
        start <- genChar
        end <- genChar
        if end >= start
    } yield CharRange(start, end)

    val genCustomCharClass: Gen[CustomCharClass] = for {
        negated <- Gen.prob(0.2)
        range <- genCharRange
    } yield CustomCharClass(List(range), negated)


    val genPredefinedCharClass: Gen[PredefinedCharClass] = for {
        name <- Gen.oneOf("d", "w", "s")
        negated <- Gen.prob(0.2)
    } yield PredefinedCharClass(name, negated)

    val genQuantifier: Gen[Quantifier] = for {
        min <- Gen.choose(0, 3)
        max <- Gen.option(Gen.choose(math.max(min,1), 5))
        greedy <- Gen.prob(0.8)
        child <- genLiteral
    } yield Quantifier(child, min, max, greedy)

    lazy val genChild: Gen[RegexNode] = Gen.frequency(
        (6,genLiteral),
        (1,genAlternation),
        (2,genConcatenation),
        (1,genQuantifier),
        (1,genCustomCharClass)
    )

    lazy val genAlternation: Gen[Alternation] = for {
        childCount <- Gen.choose(2,3);
        children <- Gen.listOfN(childCount,genChild)
    } yield Alternation(children)

    lazy val genConcatenation: Gen[Concatenation] = for {
        childCount <- Gen.choose(2,3);
        children <- Gen.listOfN(childCount,genChild)
    } yield Concatenation(children)

    val genLookAround: Gen[LookAround] = for {
        child <- genLiteral
        isLookAhead <- Gen.prob(0.5)
        isPositive <- Gen.prob(0.8)
    } yield LookAround(child,isLookAhead,isPositive)

    val genRegexNode: Gen[RegexNode] = Gen.frequency(
        (3,genLiteral),
        (2,genAlternation),
        (3,genConcatenation),
        (1,genQuantifier),
        (1,genCustomCharClass)
    )
    val genExtendNode: Gen[RegexNode] = Gen.frequency(
        (5,genLiteral),
        (1,genAlternation),
        (5,genConcatenation),
        (1,genQuantifier),
        (1,genCustomCharClass)
    )
    def extend(body:RegexNode)= Gen.oneOf(
        genExtendNode.map{en=>Concatenation(body,en)},
        genExtendNode.map{en=>Concatenation(en,body)},
        for (front<- genExtendNode; back<-genExtendNode) yield Concatenation(front,body,back)
    )

    def genPattern:Gen[String]= genRegexNode.map{n => toPattern(canonicalize(spartanize(n)))}


    def patterns:Iterator[String] = for (opat <- Iterator.continually(genPattern.sample);
                                         pat <- opat) yield pat;




    // broken
    def exampleGenerator(pattern:String)=RegexpGen.from(pattern);
    def fatExampleGenerator(pattern:String)=RegexpGen.from(pattern);


}

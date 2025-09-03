/** The PeerReview tests compare implementations against their peers.
  *
  * A random pattern is generated, and the peers are run against
  * random examples of the pattern.
  * 
  * for wholeMatch,  all engines are peers.
  *
  * generally, the DFAs are peers and the NFAs are peers. 
  * 
  * Not thrilled with the display on failure, but the information is
  * in that hairball.
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
import RegexCanonicalizer.*
import RegexGen.*
import RegexAST.*
import Util.Labeled

class PeerReview extends ScalaCheckSuite {
    override val scalaCheckTestParameters = 
        super.scalaCheckTestParameters
            .withMinSuccessfulTests(1000) // default 100
            .withMaxDiscardRatio(20) // Allows more failed shrinks before giving up  
            .withMinSize(0)
            .withMaxSize(10)
            .withWorkers(1)

    type Engines= Labeled[Iterable[RegexEngine]];

    val shady=new Engines("shady",List(Bogus,DkBrics))
    val greedy=new Engines("greedy",List(DkBrics, BricsWalk,
                                     BricsScreen,
                                     MonqJFA
                       ))
    val perl=new Engines("perl",List(JavaUtil,Joni,Florian,Re2J))
    val greedyAndPerlAndOther=new Engines("all",List(JavaUtil,Joni,Florian,Re2J,DkBrics,BricsWalk,BricsScreen,MonqJFA))

    val variousEngines=List(greedy,perl,greedyAndPerlAndOther);

    type Aspect=Labeled[(Regex,String)=>String]

    val wholeMatch= new Aspect("wholeMatch", _.hasWholeMatch(_).toString) ;
    val firstMatch= new Aspect("firstMatch", (r,t)=> r.locateFirstMatchIn(t).toString);
    val allMatch= new Aspect("All Matches", (r,t) => r.locateAllMatchIn(t).toList.toString);

    val aspects=List(wholeMatch,firstMatch,allMatch);

    type RegexGenerator=Labeled[RegexGen]

    val ascii= new RegexGenerator("ascii",RegexGen.asciiPrintable)
    val abcd= new RegexGenerator("abcd",RegexGen.abcd)
    val utf= new RegexGenerator("utf",RegexGen.utf)

    val generators=List(abcd,ascii,utf); // add utf in time

    type ExampleStyle= Labeled[String=>String];


    val unpadded= new ExampleStyle("unpadded",identity)
    val padded= new ExampleStyle("padded",s => s".*(?:$s).*(?:(?:$s).*)*)")

    property("Patterns compile"){
        val pg:Gen[String]=abcd().genRegexNode.map{rn =>
            toPattern(canonicalize(spartanize(rn)))
        }
        forAll(pg) { pat =>
            (pat.size>0) ==> {
                DkBrics.compile(pat);
                true;
            }
        }
    }

    def propTest[T](engines:Engines,
                    aspect:Aspect,
                    regexGen:RegexGenerator,
                    exampleStyle:ExampleStyle
    )={

        property(s"All ${engines.label} give same ${aspect.label} over ${regexGen.label} with ${exampleStyle.label} examples" ){
            val pg=regexGen().genRegexNode;

            forAll(pg) { rn =>
                val pat=toSpartanPattern(rn);

                (pat.size>0) ==> {
                    try {
                        val regexes=engines().map(_.compile(pat));

                        val gen=regexGen().exampleGenerator(exampleStyle()(pat))

                        forAll(gen){txt =>
                            val result2regex=regexes.groupBy{r=> aspect()(r,txt)}
                            (1 == result2regex.size) :| s"===> example='${txt}' pattern='${pat}' ${result2regex.map{(r,x) => (r,x.map(_.engineName))}}"
                        }
                        
                    } catch {
                        case e => throw new RegexException(s"Problem with pattern '$pat'",e)
                    }
                }
            }
        }
    }


    for (e<- List(perl,greedy); a<-aspects; g<-generators; s<- List(unpadded,padded)) {
        propTest(e,a,g,s)
    }

    // can only compare accross dfa and nfa with whole match
    for (e<- List(greedyAndPerlAndOther); a<-List(wholeMatch); g<-generators) {
        propTest(e,a,g,unpadded)
    }

    if (false) {
        for (e<- List(shady); a<-List(wholeMatch); g<-generators) {
            propTest(e,a,g,unpadded)
        }
    }
}





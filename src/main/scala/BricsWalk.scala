package worldofregex;

/**
 *  Using Brics to make a nonbacktracking NFA.
 *
 *  This is not very performant.  Currently doing linear search
 *  through transitions.
 *
 *  Uses Brics, but not their fault.
 */

object BricsWalk extends RegexEngine {
    val name="BricsWalk"

    import dk.brics.automaton.BricsNfaAutomaton.WalkAutomaton;
    import dk.brics.automaton.BricsUtils.auto;
    import scala.jdk.CollectionConverters.*;
    import Util.manglePattern

    inline def dprintln(inline s:String)= if false then println(s)


    def compile(pattern:String):Regex ={


        val a = try {
            WalkAutomaton(auto(manglePattern(pattern)))
        } catch {
            case e => throw new RegexException(s"error compiling /${pattern}/ mangled as /${manglePattern(pattern)}/",e);
        }
        new Regex {
            override def toString= s"BricsWalk($pattern)"

            val engineName="BricsWalk"

            def hasWholeMatch(txt:String):Boolean=a.run(txt)==txt.length

            def hasPartialMatch(txt:String):Boolean=a.hasPartialMatch(txt);


            def locateFirstMatchIn(txt:String):Option[Location]={
                // should this do hasPartialMatch first?   That would help worst case at the
                // expense of the happy path
                a.nextMatch(txt,0)
            }

            def locateAllMatchIn(txt:String):Iterator[Location]={
                // should this do hasPartialMatch first?   That would help worst case at the
                // expense of the happy path
                Iterator.unfold(0){(i) =>
                    a.nextMatch(txt,i).map{r => (r, math.max(r.end,r.start+1))}
                }
            }
        }
    }
}

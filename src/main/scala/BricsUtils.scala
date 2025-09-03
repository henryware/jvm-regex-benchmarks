/* These are shortcuts originally written for playing in the repl.
 *
 */
 
package dk.brics.automaton

object BricsUtils {
    import dk.brics.automaton.{BasicOperations => B, SpecialOperations=>S}
    import dk.brics.automaton.*
    import scala.jdk.CollectionConverters.*;
    import worldofregex.Location

    extension (a:Automaton){
        inline def getInitial=a.initial
        inline def acceptsInitial=a.initial.accept

        def ~(that:Automaton)=B.concatenate(a,that)
        def -(that:Automaton)=B.minus(a,that);
        def |(that:Automaton)=B.union(a,that);
        def ^(that:Automaton)=S.overlap(a,that);
        def &(that:Automaton)=B.intersection(a,that);
        def reverse = { // premissions looser vs upstream
            val ret=a.cloneExpanded()
            S.reverse(ret); // return value informational: payload in mutable ret
            ret
        }
        def ? =B.optional(a);
        def ra =new dk.brics.automaton.RunAutomaton(a,true);
    }

    val DOTSTAR=BasicAutomata.makeAnyString;

    def auto(txt:String)={
        new RegExp(txt).toAutomaton
    }

    extension (sc: StringContext){
        def a(args: Any*):Automaton= auto(sc.parts.iterator.next)
    }

    extension (s:State){
        // there are many ways this could be speeded up.   
        def stepBy(c:Char):Iterator[State]={
            s.transitions.asScala.iterator.filter{t => t.min <= c & c <= t.max}.map{_.to}
        }
        // relaxes permissions
        inline def getNumber={
            s.number
        }
    }

}

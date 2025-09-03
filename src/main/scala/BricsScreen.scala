/* This is a alternative driver for the dk.brics Automaton.
 *
 * It seeks to minimize the `O(N²)` behavior on 'almost' partial matches.
 * 
 * It does this by finding all possible start points with a scan using
 * `(re ~ .*).reverse`.  Then it only looks for locations which start
 * at those end points
 *  
 */

package worldofregex;

import scala.util.control.Breaks._
import Util.doWhile;

object BricsScreen extends RegexEngine {
    val name="BricsScreen"

    import dk.brics.automaton.{RunAutomaton, RegExp, BasicAutomata}
    import dk.brics.automaton.BricsUtils.*;
    import Util.manglePattern

    inline def dprintln(inline s:String)= if false then println(s)

    extension (run:RunAutomaton){
        /* plain old greedy run is available as the 'run' method in RunAutomata */

        /* use the Automaton to run forward to find the offset of the first accepted state */
        def lazyRun(txt:String, start:Int=0): Int = {
            var p = run.getInitialState;
            val len=txt.length()
            if (run.isAccept(p)){
                start
            } else if (len==0 | start>=len){
                -1
            } else {
                var i = start
                while ({
                           p=run.step(p,txt(i));
                           i = i+1;
                           i<len && !run.isAccept(p)
                       }){}
                if (run.isAccept(p)){
                    i
                } else {
                    -2
                }
            }
        }
        /* use the Automaton to run backward (=nur) to find the offset of the shortest accepted state */
        def lazyNur(txt:String, start:Int= -1, finish:Int= 0): Int = {
            var i=if (start == -1) {
                txt.length()
            } else {
                start
            }
            var p = run.getInitialState;
            if (run.isAccept(p)){
                i
            } else if (i==finish){
                -1
            } else {
                while ({
                           i -= 1;
                           p=run.step(p,txt(i));
                           i>finish && !run.isAccept(p)
                       }){}
                if (run.isAccept(p)){
                    i
                } else {
                    -2
                }
            }
        }
        /* use the Automaton to run forward to find the offset of the last accepted state */
        def greedyRun(txt:String, start:Int=0): Int = {
            val len=txt.length()
            if (len==0 | start>=len){
                if (run.isAccept(run.getInitialState)){
                    start
                } else {
                    -1
                }
            } else {
                var victory= -1;
                var p = run.getInitialState;
                var i = start
                while ({
                           if (run.isAccept(p)){
                               victory=i
                           }
                           p=run.step(p,txt(i));
                           i=i+1;
                           i<len && p>=0
                       }){}
                if (p>=0 && run.isAccept(p)){
                    i
                } else {
                    victory
                }
            }
        }
        /* use the Automaton to run backward to find the offset of the longest accepted state */
        def greedyNur(txt:String, start:Int= -1, finish:Int= 0): Int = {
            var i=if (start == -1) {
                txt.length()
            } else {
                start
            }

            if (i==finish){
                if (run.isAccept(run.getInitialState)){
                    finish
                } else {
                    -1
                }
            } else {
                var victory= -1;
                var p = run.getInitialState;
                dprintln(s"""i=${i} p=${p} c=NA accept=${p>=0 && run.isAccept(p)}""")

                while ({
                           if (run.isAccept(p)){
                               victory=i
                           }
                           i=i-1;
                           p=run.step(p,txt(i));
                           dprintln(s"i=${i} p=${p} c=${txt(i)} accept=${p>=0 && run.isAccept(p)}")
                           i>finish && p>=0
                       }){}
                if (p>=0 && run.isAccept(p)){
                    i
                } else {
                    victory
                }
            }
        }
        /* Run backward finding all accepting states.  These are returned in
         * accending order.  This is useful in finding the possible
         * start positions.*/
        def fullNur(txt:String): Iterable[Int] = {
            var i=txt.length()
            var accum=scala.collection.mutable.ArrayBuffer[Int]();

            var p = run.getInitialState;

            if (run.isAccept(p)){
                accum+=i
            }
            while (i > 0){
                i=i-1;
                p=run.step(p,txt(i));
                if (run.isAccept(p)){
                    accum+=i
                }
            }


            accum.reverse
            
        }
    }
    def compile(pattern:String):Regex ={
        val auto = try {
            new RegExp(manglePattern(pattern)).toAutomaton;
        } catch {
            case e => throw new RegexException(s"error compiling /${pattern}/ mangled as /${manglePattern(pattern)}/",e);
        }
        val plain= auto.ra
        val star=(BasicAutomata.makeAnyString ~ auto).ra
        val rats=(auto ~ BasicAutomata.makeAnyString).reverse.ra // rats= star backwards


        new Regex {
            override def toString= s"TBA($pattern)"

            val engineName="BricsTBA"

            def hasWholeMatch(txt:String):Boolean=plain.run(txt)

            // this was backwards (ie using rats.lazyNur) to avoid
            // needing to compile 'star'.  That was unfair as the
            // sample texts have the match at the end of a pile of
            // noise and `rats` found it right away.
            def hasPartialMatch(txt:String):Boolean=star.lazyRun(txt) >= 0;

            private val acceptsEmpty = auto.acceptsInitial // Check if the original automaton matches an empty string


            private def nextMatch(txt:String, start:Int=0, possibleStarts:List[Int])={

                val finish=plain.greedyRun(txt,start)
                if (finish > -1) {
                    Some(Location(start,finish))
                } else {
                    None
                }
            }

            /* This may not be the best implementation because it is `O(N)` even
             * when the match is right away.  But it's the right
             * implementation _here_ because the unscreened
             * implementation is used in DkBrics: no point in
             * repeating that here.
             */ 
            def locateFirstMatchIn(txt:String):Option[Location]={
                locateAllMatchIn(txt).nextOption
            }

            def locateAllMatchIn(txt:String):Iterator[Location]={
                val it=rats.fullNur(txt).iterator.buffered
                var index=0;

                @annotation.tailrec def findOne():Option[Location]={
                    while (!it.isEmpty && it.head < index){
                        it.next
                    }
                    if (it.isEmpty){
                        None
                    } else {
                        val finish=plain.greedyRun(txt,it.head)
                        if (finish>= 0) {
                            index=math.max(finish,index+1)
                            Some(Location(it.head, finish))
                        } else {
                            findOne()
                        }
                    }
                }

                new Iterator[Location]{
                    var onDeck=findOne()
                    var uptoDate=true
                    def update()={
                        if (!uptoDate){
                            onDeck=findOne();
                            uptoDate=true;
                        }
                    }
                    override def hasNext= {
                        update();
                        onDeck.isDefined
                    }
                    override def next={
                        update();
                        val ret=onDeck.get;
                        uptoDate=false;
                        ret;
                    }
                }

            }

        }
    }
}

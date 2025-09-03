package dk.brics.automaton

object BricsNfaAutomaton {
    import scala.jdk.CollectionConverters.*;
    import worldofregex.Location
    import BricsUtils.*

    case class NfaDriver(a:Automaton) {
        import worldofregex.SparseSet;

        Automaton.setStateNumbers(a.getStates());

        val states={
            val allStates = a.getStates();
            val temp=new Array[State](allStates.size);
            allStates.asScala.foreach{s=> temp(s.number)=s}
            IArray.unsafeFromArray(temp)
        }
        val acceptsEmpty=a.initial.accept;
        def run(txt: String, start:Int=0):Int={
            var actives=new SparseSet(states.size)
            var destinations=new SparseSet(states.size)
            val _ =actives.add(a.initial.number)
            var i=start;
            var victory = if (acceptsEmpty) i  else -1;
            while (i >=0 && i<txt.length && actives.size>0){
                val c=txt(i);
                i += 1;
                for (active<-actives.iterator;
                     nextState<-states(active).stepBy(c)) do {
                    if (nextState.accept) {
                        victory=i;
                    }
                    destinations.add(nextState.getNumber)
                }
                val tmp=actives
                actives=destinations
                destinations=tmp;
                destinations.clear()
            }
            victory
        }
        // run backwards
        def nur(txt: String, start:Int= -1, end:Int= 0):Int={
            var actives=new SparseSet(states.size)
            var destinations=new SparseSet(states.size)
            val _ = actives.add(a.initial.number)
            var i= if (start== -1) txt.length else start
            var victory = if (acceptsEmpty) i  else -1;
            while (i>end && actives.size>0){
                i -= 1;

                val c=txt(i);
                for (active<-actives.iterator;
                     nextState<-states(active).stepBy(c)) do {
                    if (nextState.accept) {
                        victory=i;
                    }
                    destinations.add(nextState.getNumber)
                }
                val tmp=actives
                actives=destinations
                destinations=tmp;
                destinations.clear()
            }
            victory
        }
        // run forwards until first success
        def lazyRun(txt: String, start:Int=0):Int={
            var actives=new SparseSet(states.size)
            var destinations=new SparseSet(states.size)
            val _ = actives.add(a.initial.number)
            var accept=a.initial.accept
            var i=start;
            while (i<txt.length && !accept){
                accept=false;
                val c=txt(i);
                i += 1;
                for (active<-actives.iterator;
                     nextState<-states(active).stepBy(c)) do {
                    accept |= nextState.accept;
                    destinations.add(nextState.getNumber)
                }
                val tmp=actives
                actives=destinations
                destinations=tmp;
                destinations.clear()
            }
            if (accept) i else -1;
        }
    }
    
    trait WalkAutomaton{
        def nextMatch(txt:String, start:Int=0):Option[Location]
        def run(s:String, start:Int=0):Int;
        def hasPartialMatch(txt:String):Boolean
    }
    def WalkAutomaton(a:Automaton)={

        val plain=NfaDriver(a)
        val star=NfaDriver(BasicAutomata.makeAnyString ~ a)
        val acceptsEmpty = a.initial.accept // Check if the original automaton matches an empty string

        new WalkAutomaton {
            def nextMatch(txt:String, start:Int=0):Option[Location]= {
                if (start > txt.length || start<0) {
                    None
                } else if (acceptsEmpty){
                    // definitely have a zero length match
                    // maybe we can get better with a greedy run
                    val ran=plain.run(txt, start)
                    Some(Location(start,ran))
                } else {
                    @annotation.tailrec def scanToNextMatch(begin:Int):Option[Location]={
                        if (begin==txt.length) {
                            // we know we don't accept empty, we are done
                            None
                        } else {
                            val ran=plain.run(txt, begin)
                            if (ran >= 0) {
                                Some(Location(begin,ran))
                            } else {
                                scanToNextMatch(begin+1)
                            }
                        }
                    }
                    scanToNextMatch(start)
                }
            }

            def hasPartialMatch(txt:String): Boolean = star.lazyRun(txt) >= 0

            def run(txt:String, start:Int=0): Int = plain.run(txt,start)
            override def toString: String = a.toString
        }
    }
}

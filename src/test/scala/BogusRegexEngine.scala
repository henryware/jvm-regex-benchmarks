package tests

import worldofregex.*

/** A Bogus Regex Engine which gives usually incorrect results.  For testing tests. */

object Bogus extends RegexEngine {
    def name:String="Bogus"

    def compile(pattern:String):Regex= new Regex{

        override def toString= s"Bogus($pattern)"

        val engineName="Bogus"

        /* whole string match */
        def hasWholeMatch(txt:String):Boolean= (pattern.size+txt.size)%2==0

        /* partial match exists */
        def hasPartialMatch(txt:String):Boolean=  (pattern.size+txt.size)%2==0

        def locateFirstMatchIn(txt:String):Option[Location]={
            if ( (pattern.size+txt.size)%2==0){
                None
            } else {
                Some(Location(0,0));
            }
        }

        def locateAllMatchIn(txt:String):Iterator[Location]={
            if ( (pattern.size+txt.size)%2==0){
                Iterator.empty;
            } else {
                (0 to txt.size).iterator.map{i=>Location(i,i)}
            }
        }

    }
}





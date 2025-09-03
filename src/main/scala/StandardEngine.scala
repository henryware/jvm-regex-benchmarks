package worldofregex

/*
 * Several regex implementations mimic the java.util.regex class
 * heirarchy for source code compatability.  We need a little more
 * than that here: but without any inheritance
 */

trait StandardEngine extends RegexEngine {

    type PatternImpl;

    def pcompile(pattern:String):PatternImpl;
    def name:String
    def pmatcher(rx: PatternImpl, txt:String):M

    trait M {
        def matches:Boolean
        def find:Boolean
        def groupCount:Int
        def start:Int
        def `end`:Int
        def start(group:Int):Int
        def `end`(group:Int):Int
    }


    def compile(pattern:String):Regex= new Regex{
        override def toString= s"${name}($pattern)"
        def engineName=name

        private val rx= {
            try {
                pcompile(pattern);
            } catch {
                case e:Exception => throw new RegexException(s"Error parsing ${toString}",e)
            }
        }

        def hasWholeMatch(txt:String):Boolean=pmatcher(rx,txt).matches

        def hasPartialMatch(txt:String):Boolean=pmatcher(rx,txt).find

        inline private def submatches(m:M)={
            if (m.groupCount==0) {
                Nil
            } else {
                (1 to m.groupCount).map{i => (m.start(i),m.end(i))}
            }
        }

        private def findMatch(m:M)={
            m.find match {
                case true => Some(Location(m.start,m.end,submatches(m)));
                case false => None
            }
        }

        def locateFirstMatchIn(txt:String):Option[Location]={
            findMatch(pmatcher(rx,txt))
        }

        def locateAllMatchIn(txt:String):Iterator[Location]={
            val m=pmatcher(rx,txt)
            Iterator.continually(findMatch(m)).takeWhile(_ != None).flatten
        }

    }

}


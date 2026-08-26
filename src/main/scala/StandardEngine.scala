package worldofregex

/*
 * Several regex implementations mimic the java.util.regex class
 * hierarchy giving source code compatability.  This is a base class
 * for the relevant adapters. 
 */

trait StandardEngine extends RegexEngine {

    type PatternImpl;

    def pcompile(pattern:String):PatternImpl;
    def name:String
    def pmatcher(rx: PatternImpl, txt:String):M

    trait M {
        // Rebind this matcher to new input, reusing the underlying object
        // instead of allocating a fresh one per call.  Returns self so
        // callers can write `reset(txt).matches()`.
        def reset(txt:String):M
        // these match the Java signatures for `export` friendliness.
        def matches():Boolean
        def find():Boolean
        def groupCount():Int
        def start():Int
        def `end`():Int
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

        // Each matcher owns one underlying java-style Matcher and reset()s it
        // per call, so no allocation happens on the hot path.  The Matcher is
        // not thread-safe (java's contract); callers use one per thread.
        def matcher():Matcher= new Matcher {

            private val m= pmatcher(rx,"")

            def hasWholeMatch(txt:String):Boolean= m.reset(txt).matches()

            def hasPartialMatch(txt:String):Boolean= m.reset(txt).find()

            inline private def submatches(m:M)={
                if (m.groupCount()==0) {
                    Nil
                } else {
                    (1 to m.groupCount()).map{i => (m.start(i),m.end(i))}
                }
            }

            private def findMatch(m:M)={
                m.find() match {
                    case true => Some(Location(m.start(),m.end(),submatches(m)));
                    case false => None
                }
            }

            def locateFirstMatchIn(txt:String):Option[Location]={
                findMatch(m.reset(txt))
            }

            def locateAllMatchIn(txt:String):Iterator[Location]={
                val mr=m.reset(txt)
                Iterator.continually(findMatch(mr)).takeWhile(_.isDefined).flatten
            }
        }

    }

}


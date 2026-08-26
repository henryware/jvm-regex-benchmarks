
package worldofregex {
    // stateless interface
    trait RegexEngine{
        def name:String;

        def version:String

        def compile(pattern:String):Regex
    }

    /* A compiled pattern.  Immutable and safe to share across threads,
     * mirroring java.util.regex.Pattern.  Matching state lives on Matcher. */
    trait Regex {
        def engineName:String

        /* Create a matcher for this pattern.  Safe to call concurrently, but
         * the returned Matcher is NOT thread-safe: use one per thread. */
        def matcher():Matcher
    }

    /* A reusable matcher bound to one pattern.  NOT thread-safe (mirrors
     * java.util.regex.Matcher): a single Matcher may be reused across many
     * inputs on one thread, but must not be shared between threads, and its
     * operations must not be interleaved (e.g. a locateAllMatchIn iterator
     * must be consumed before calling another method on the same Matcher). */
    trait Matcher {
        /* whole string match */
        def hasWholeMatch(txt:String):Boolean

        /* partial match exists */
        def hasPartialMatch(txt:String):Boolean

        def locateFirstMatchIn(txt:String):Option[Location]

        def locateAllMatchIn(txt:String):Iterator[Location]

    }

    case class Location (start:Int, end:Int, subregions:Seq[(Int,Int)]=Nil){
        override def toString()={s"(${start},${end})"}
    };

    class RegexException(msg:String, base:Throwable=null) extends Exception(msg,base);
}

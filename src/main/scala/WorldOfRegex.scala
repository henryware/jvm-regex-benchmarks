
package worldofregex {
    // stateless interface
    trait RegexEngine{
        def name:String;

        def version:String

        def compile(pattern:String):Regex
    }

    trait Regex {
        def engineName:String
        
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

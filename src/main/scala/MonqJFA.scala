package worldofregex;

object MonqJFA extends RegexEngine {
    val name="MonqJFA"
    import monq.jfa.*;
    def compile(pattern:String):Regex ={
        val startPin= pattern(0)=='^'
        val endPin= pattern(pattern.length-1)=='$'

        val rx= if (startPin || endPin) {
            val startChar=if startPin then 1 else 0;
            val endChar=if endPin then pattern.length-1 else pattern.length;
            new Regexp(Util.manglePattern(pattern.substring(startChar,endChar)))
        } else {
            new Regexp(Util.manglePattern(pattern))
        }
        new Regex {
            override def toString= s"MonqJFA($pattern)"

            val engineName="MonqJFA"

            def hasWholeMatch(txt:String):Boolean=rx.matches(txt,0)

            def hasPartialMatch(txt:String):Boolean=
                if (startPin && endPin){
                    hasWholeMatch(txt);
                } else {
                    findMatchIn(txt,0) != None
                }
        
            def findMatchIn(txt:String, index:Int):Option[Location]={
                if (index>txt.length) {
                    None
                } else {
                    rx.find(txt,index) match {
                        case -1 => None
                        case i => {
                            val stop=i+rx.length;
                            if ((startPin && i>0) ||
                                    (endPin && stop<txt.length)) {
                                None
                            } else {
                                Some(Location(i,stop));
                            }
                        }
                    }
                }
            }


            def locateFirstMatchIn(txt:String):Option[Location]={
                findMatchIn(txt,0)
            }
            
            def locateAllMatchIn(txt:String):Iterator[Location]={
                var index=0;
                def next={
                    val ret=findMatchIn(txt,index)
                    ret.foreach{r => index= if(index==r.end) index +1 else r.end}
                    ret
                }
                Iterator.continually(next).takeWhile(_ !=None).flatten
            }

            def replaceAllIn(txt: String, replacement: String): String=  ???
        }
    }
}

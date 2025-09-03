package worldofregex;


object KMY extends RegexEngine{
    val name="KMY"

    import kmy.regex.parser.*
    import kmy.regex.tree.*
    import kmy.regex.compiler.*
    import kmy.regex.interp.*
    import kmy.regex.jvm.*
    import kmy.regex.util.{Regex => KRegex}

    private def parse(pattern:String):RNode={
        (new RParser()).parse(pattern)
    }

    private def getRE(pattern:String, machine:RMachine)={
        val comp = new RCompiler(machine)
        comp.compile(parse(pattern),pattern)
        val re = machine.makeRegex()
        re
    }

    private def getDebugRE(pattern:String)={
        getRE(pattern,new RDebugMachine())
    }

    private def getInterpRE(pattern:String)={
        getRE(pattern,new RInterpMachine())
    }

    private def getJvmRE(pattern:String)={
        getRE(pattern,new RJavaClassMachine())
    }

    def compile(pattern:String)= new Regex{
        //println(s"parse $pattern")
        val anchored=Util.anchorPattern(pattern);
        //println(s"parse $anchored")
        val re=getJvmRE(pattern);
        val are=getJvmRE(anchored)

        override def toString(): String = s"KMY(${re.toString})"

        val engineName="KMY"

        def hasWholeMatch(txt:String):Boolean= {
            /* Docs say to use 'matchesWhole', but that fails on 
             "aa" =~ /a{1}a/
             "abc" =~ /c$/
             and others
             whereas an anchored search just works */
            are.searchOnce(txt)
        }

        def hasPartialMatch(txt:String):Boolean= re.searchOnce(txt)

        def locateFirstMatchIn(txt:String):Option[Location]= {
            if (re.searchOnce(txt)) {
                Some(Location(re.getMatchStart,re.getMatchEnd))
            } else {
                None
            }
        }

        def locateAllMatchIn(txt:String):Iterator[Location]= {

            val arr=txt.toCharArray
            re.init(arr,0,arr.length)
            Iterator.continually{if re.search then Some(Location(re.getMatchStart,re.getMatchEnd)) else None}.
                takeWhile(_ != None).flatten
        }

    }
}


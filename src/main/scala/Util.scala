package worldofregex

object Util{

    case class Labeled[T](label:String, item:T) {def apply()=item}

    // doesn't handle recursive functions.  Sad!
    case class Memoize[T:scala.reflect.ClassTag,R](f:Function1[T,R]) extends Function1[T,R] {
        val cache=new scala.collection.mutable.HashMap[T,R];

        def apply(x:T)={
            cache.getOrElseUpdate(x,f(x))
        }
    }

    @annotation.tailrec def doWhile[T](body: =>T)(guard: =>Boolean):T={
        val t:T=body;
        if (!guard){
            t
        } else {
            doWhile(body)(guard)
        }
    }

    extension [A](a: A) {
        inline def |>[B](inline f: A => B): B = f(a)
    }


    /* hack for the DFAs which don't understand these tested items */
    def manglePattern(pat:String)={  
        // all of these are leaky (ie if preceded by a backslash). No matter
        pat.replaceAll("""\\d""","[0-9]").
            replaceAll("""\\D""","[^0-9]").
            replaceAll("""\\s""","""[ \t\n\x0B\f\r]""").
            replaceAll("""\\S""","""[^ \t\n\x0B\f\r]""").
            replaceAll("""\\w""","""[a-zA-Z_0-9]""").
            replaceAll("""\\w""","""[^a-zA-Z_0-9]""").
            replaceAll("""\(\?[:]""","""(""")
    }

    /* Joni and KMY don't always do the right thing on whole matches, but like anchored patterns */
    def anchorPattern(pat:String)={
        s"""^(?:${pat})$$"""
    }
}

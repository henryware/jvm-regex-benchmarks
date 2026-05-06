package worldofregex

object Re2J extends StandardEngine {
    import com.google.re2j.{Pattern => Pat, Matcher=>Mat}
    type PatternImpl=Pat

    def name="Re2J"
    def pcompile(pattern: String): Pat = Pat.compile(pattern);
    def pmatcher(rx: Pat, txt: String): M = new M{
        private val m=rx.matcher(txt);
        export m.{matches, find, groupCount, start, `end`, replaceAll}
    }
}




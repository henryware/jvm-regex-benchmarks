package worldofregex

object JavaUtil extends StandardEngine {
    import java.util.regex.{Pattern => Pat, Matcher=>Mat}
    type PatternImpl=Pat

    def name="JavaUtil"
    def pcompile(pattern: String): Pat = Pat.compile(pattern);
    def pmatcher(rx: Pat, txt: String): M = new M{
        private val m=rx.matcher(txt);
        export m.{matches, find, groupCount, start, `end`}
    }
}

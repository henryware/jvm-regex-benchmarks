package worldofregex

object Florian extends StandardEngine {
    import com.florianingerl.util.regex.{Pattern=>Pat, Matcher=> Mat}
    type PatternImpl=Pat

    def name="Florian"
    def version=LibraryVersion.fromClass(classOf[com.florianingerl.util.regex.Pattern])
    def pcompile(pattern: String): Pat = Pat.compile(pattern);
    def pmatcher(rx: Pat, txt: String): M = new M{
        private val m=rx.matcher(txt);
        export m.{matches, find, groupCount, start, `end`, replaceAll}
    }
}




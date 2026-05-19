package worldofregex

object JiTrex extends StandardEngine {
    import com.humio.jitrex.{Pattern => Pat, Matcher=>Mat}
    type PatternImpl=Pat

    def name="JiTrex"
    def version=LibraryVersion.fromClass(classOf[com.humio.jitrex.Pattern])
    def pcompile(pattern: String): Pat = Pat.compile(pattern);
    def pmatcher(rx: Pat, txt: String): M = new M{
        private val m=rx.matcher(txt);
        export m.{matches, find, groupCount, start, `end`}
    }
}

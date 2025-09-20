package worldofregex

object JiTrex extends StandardEngine {
    import com.humio.jitrex.{Pattern => Pat, Matcher=>Mat}
    type PatternImpl=Pat

    def name="JiTrex"
    def pcompile(pattern: String): Pat = Pat.compile(pattern);
    def pmatcher(rx: Pat, txt: String): M = new M{
        private val m=rx.matcher(txt);
        def matches:Boolean=m.matches()
        def find:Boolean=m.find()
        def groupCount:Int=m.groupCount()
        def start:Int=m.start()
        def `end`:Int=m.end()
        def start(group:Int):Int=m.start(group)
        def `end`(group:Int):Int=m.end(group)
    }
}

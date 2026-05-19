package worldofregex;

/** Adapter for Pattern Search Algorithms (amygdalum)
  * https://patternsearchalgorithms.amygdalum.net/
  *
  * DFA-based regex library focused on efficient searching of large texts.
  * No groups, no back-references.  Default search mode is
  * LONGEST_NON_OVERLAPPING (leftmost-longest, POSIX-style).
  *
  * We compile two Patterns per pattern: one with OptimizationTarget.MATCH
  * (used for hasWholeMatch) and one with OptimizationTarget.SEARCH (used for
  * partial match, locateFirst and locateAll).  The SEARCH-optimized
  * backmatcher can give wrong whole-match results on some patterns (observed
  * with property-test inputs), so matches() is routed through the dedicated
  * MATCH-optimized pattern.
  *
  * DOT_ALL is enabled to align with the other POSIX-style engines
  * (DkBrics, MonqJFA, BricsScreen, BricsWalk) where '.' also matches '\n'.
  */

import net.amygdalum.patternsearchalgorithms.pattern.{Pattern, OptimizationTarget, SearchMode, RegexOption}
import worldofregex.Util.manglePattern

object Amygdalum extends RegexEngine {
    val name="Amygdalum"
    val version=LibraryVersion.fromClass(classOf[net.amygdalum.patternsearchalgorithms.pattern.Pattern])

    def compile(pattern:String):Regex ={
        val mangled = manglePattern(pattern)
        val (matchPat, searchPat) = try {
            (
                Pattern.compile(mangled, OptimizationTarget.MATCH, SearchMode.LONGEST_NON_OVERLAPPING, RegexOption.DOT_ALL),
                Pattern.compile(mangled, OptimizationTarget.SEARCH, SearchMode.LONGEST_NON_OVERLAPPING, RegexOption.DOT_ALL)
            )
        } catch {
            case e: Throwable => throw new RegexException(s"error compiling /${pattern}/ mangled as /${mangled}/",e);
        }

        new Regex {
            override def toString= s"Amygdalum($pattern)"

            val engineName="Amygdalum"

            def hasWholeMatch(txt:String):Boolean=matchPat.matcher(txt).matches()

            def hasPartialMatch(txt:String):Boolean=searchPat.matcher(txt).find()

            def locateFirstMatchIn(txt:String):Option[Location]={
                val m=searchPat.matcher(txt)
                if (m.find()) Some(Location(m.start().toInt, m.end().toInt))
                else None
            }

            /* The library's find() does not advance past zero-width matches,
             * so we drive progression ourselves: match against substrings and
             * always advance at least one position past the start of each
             * reported match.
             */
            def locateAllMatchIn(txt:String):Iterator[Location]={
                var index=0
                def nextMatch():Option[Location]={
                    if (index>txt.length) None
                    else {
                        val m=searchPat.matcher(txt.substring(index))
                        if (m.find()){
                            val s=m.start().toInt + index
                            val e=m.end().toInt + index
                            index = math.max(e, s+1)
                            Some(Location(s,e))
                        } else None
                    }
                }
                Iterator.continually(nextMatch()).takeWhile(_.isDefined).flatten
            }
        }
    }
}

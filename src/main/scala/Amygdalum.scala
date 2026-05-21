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

            /* Since 0.1.4 the library's find() advances past zero-width
             * matches on its own, so a single matcher drives the iteration.
             */
            def locateAllMatchIn(txt:String):Iterator[Location]={
                val m=searchPat.matcher(txt)
                Iterator.continually(
                    if (m.find()) Some(Location(m.start().toInt, m.end().toInt)) else None
                ).takeWhile(_.isDefined).flatten
            }
        }
    }
}

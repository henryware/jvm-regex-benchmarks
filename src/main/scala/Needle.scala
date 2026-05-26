package worldofregex;

/** Adapter for Needle (https://github.com/hyperpape/needle)
  *
  * DFA-based regex compiler that emits a fresh JVM class per pattern.  The
  * 0.0.1 release of needle-compiler is much barer than the project README
  * (which describes the in-development interface): no flags, no anchors, no
  * non-capturing groups.  Capabilities observed at runtime against 0.0.1:
  *  - basic regex syntax, character classes (\d, \s, \w), groups (no
  *    submatch extraction).
  *  - default match semantics are leftmost-first (Perl-style).
  *  - '.' matches '\n' by default (no DOTALL flag in 0.0.1).
  *  - anchors ^/$ raise RegexSyntaxException.
  *  - non-capturing '(?:...)' raises RegexSyntaxException; mangled to '(...)'.
  *  - find() does not advance past zero-width matches; locateAllMatchIn drives
  *    iteration via the bounded find(start, end) overload.
  */

import java.util.concurrent.atomic.AtomicLong
import com.justinblank.strings.{DFACompiler, Pattern, MatchResult}
import worldofregex.Util.manglePattern

object Needle extends RegexEngine {
    val name="Needle"
    val version=LibraryVersion.fromClass(classOf[com.justinblank.strings.DFACompiler])

    /* Each call to DFACompiler.compile defines a new JVM class — the class
     * name must be unique within the classloader for the lifetime of the
     * process. */
    private val classCounter = new AtomicLong(0L)

    def compile(pattern:String):Regex ={
        val mangled = manglePattern(pattern)
        val className = s"NeedleRegex_${classCounter.incrementAndGet()}"
        val compiled = try {
            DFACompiler.compile(mangled, className)
        } catch {
            case e: Throwable => throw new RegexException(s"error compiling /${pattern}/ mangled as /${mangled}/",e);
        }
        regexFromPattern(compiled, pattern, name)
    }

    /* Wrap a needle Pattern in the project's Regex trait.  Shared with the
     * compile-time macro-baked path (worldofregex.macros.Needle) so the
     * zero-width iteration logic lives in exactly one place. */
    def regexFromPattern(compiled: Pattern, source: String, engine: String): Regex = {
        /* Probed once so locateAllMatchIn can emit the end-of-string empty
         * match without calling find(len, len), which throws in 0.0.1. */
        val acceptsEmpty = compiled.matcher("").matches()

        new Regex {
            override def toString= s"$engine($source)"

            val engineName=engine

            def hasWholeMatch(txt:String):Boolean=compiled.matcher(txt).matches()

            def hasPartialMatch(txt:String):Boolean=compiled.matcher(txt).containedIn()

            def locateFirstMatchIn(txt:String):Option[Location]={
                val r=compiled.matcher(txt).find()
                if (r.matched) Some(Location(r.start, r.end)) else None
            }

            /* Drive iteration via bounded find(start, end) so zero-width
             * matches don't trap us — the un-bounded find() does not advance
             * past a (k,k) match.  When we've walked to pos == len we can't
             * call find (0.0.1 throws on an empty range); emit the trailing
             * empty match via the pre-probed acceptsEmpty flag.
             */
            def locateAllMatchIn(txt:String):Iterator[Location]={
                val m = compiled.matcher(txt)
                val len = txt.length
                var pos = 0
                var tailEmitted = false
                def nextMatch(): Option[Location] = {
                    if (pos < len) {
                        val r = m.find(pos, len)
                        if (r.matched){
                            pos = math.max(r.end, r.start + 1)
                            Some(Location(r.start, r.end))
                        } else {
                            pos = len
                            if (acceptsEmpty && !tailEmitted){
                                tailEmitted = true
                                Some(Location(len, len))
                            } else None
                        }
                    } else if (pos == len && acceptsEmpty && !tailEmitted) {
                        tailEmitted = true
                        Some(Location(len, len))
                    } else None
                }
                Iterator.continually(nextMatch()).takeWhile(_.isDefined).flatten
            }
        }
    }
}

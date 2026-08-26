package worldofregex;

/** Adapter for Needle (https://github.com/hyperpape/needle)
  *
  * DFA-based regex compiler that emits a fresh JVM class per pattern.
  * Capabilities observed at runtime against 0.0.2:
  *  - basic regex syntax, character classes (\d, \s, \w), capturing and
  *    non-capturing groups (no submatch extraction).
  *  - flags: DOTALL, CASE_INSENSITIVE, UNICODE_CASE, UNICODE_CHARACTER_CLASS,
  *    LEFTMOST_LONGEST.  We compile with the defaults (leftmost-first,
  *    '.' does not match '\n').
  *  - anchors ^/$ still raise RegexSyntaxException.
  *  - find() returns boolean; start/end live on the Matcher.  The unbounded
  *    find() does not advance past a zero-width match, so locateAllMatchIn
  *    drives iteration via the bounded find(start, end) overload.
  */

import java.util.concurrent.atomic.AtomicLong
import com.justinblank.strings.{DFACompiler, Pattern}
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
         * match without calling find(len, len). */
        val acceptsEmpty = compiled.matcher("").matches()

        new Regex {
            override def toString= s"$engine($source)"

            val engineName=engine

            def matcher():Matcher= new worldofregex.Matcher {
                def hasWholeMatch(txt:String):Boolean=compiled.matcher(txt).matches()

                def hasPartialMatch(txt:String):Boolean=compiled.matcher(txt).containedIn()

                def locateFirstMatchIn(txt:String):Option[Location]={
                    val m=compiled.matcher(txt)
                    if (m.find()) Some(Location(m.start(), m.end())) else None
                }

                /* Drive iteration via bounded find(start, end) so zero-width
                 * matches don't trap us — the unbounded find() does not advance
                 * past a (k,k) match.  At pos == len emit the trailing empty
                 * match via the pre-probed acceptsEmpty flag.
                 */
                def locateAllMatchIn(txt:String):Iterator[Location]={
                    val m = compiled.matcher(txt)
                    val len = txt.length
                    var pos = 0
                    var tailEmitted = false
                    def nextMatch(): Option[Location] = {
                        if (pos < len) {
                            if (m.find(pos, len)){
                                val start=m.start()
                                val end=m.end()
                                pos = math.max(end, start + 1)
                                Some(Location(start, end))
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
}

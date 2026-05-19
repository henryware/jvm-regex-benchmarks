/**
  *  Adapter for the Joni engine using UTF-16LE bytes.
  *
  *  Avoids the per-call UTF-8 transcode tax of the default Joni
  *  adapter: JVM Strings are UTF-16 internally, so handing UTF-16LE
  *  bytes to Joni is cheap.  Byte→char position translation is
  *  trivial (bytePos / 2), so no mapping table is needed.
  */

package worldofregex

import java.nio.charset.StandardCharsets

object JoniUTF16 extends RegexEngine {
    val name = "JoniUTF16"
    val version = LibraryVersion.fromClass(classOf[org.joni.Regex])

    import org.joni.{Regex => JoniRegex, Matcher => JoniMatcher, Option => JoniOption}
    import org.jcodings.specific.UTF16LEEncoding

    def compile(pattern: String): Regex = new Regex {
        override def toString = s"JoniUTF16($pattern)"

        val engineName = "JoniUTF16"

        private val pat = pattern.getBytes(StandardCharsets.UTF_16LE)
        private val compiledRegex: JoniRegex =
            new JoniRegex(pat, 0, pat.length, JoniOption.NONE, UTF16LEEncoding.INSTANCE)

        private val anchoredRegex = {
            val anchored = Util.anchorPattern(pattern).getBytes(StandardCharsets.UTF_16LE)
            new JoniRegex(anchored, 0, anchored.length, JoniOption.NONE, UTF16LEEncoding.INSTANCE)
        }

        def hasWholeMatch(txt: String): Boolean = {
            val bytes = txt.getBytes(StandardCharsets.UTF_16LE)
            val matcher = anchoredRegex.matcher(bytes)
            bytes.length == matcher.`match`(0, bytes.length, JoniOption.FIND_LONGEST)
        }

        def hasPartialMatch(txt: String): Boolean = {
            val bytes = txt.getBytes(StandardCharsets.UTF_16LE)
            val matcher = compiledRegex.matcher(bytes)
            matcher.search(0, bytes.length, JoniOption.NONE) >= 0
        }

        private case class Finder(txt: String) {
            val bytes = txt.getBytes(StandardCharsets.UTF_16LE)
            val matcher = compiledRegex.matcher(bytes)
            val len = bytes.length

            var currentPos = 0

            def findMatch(): Option[Location] = {
                if (matcher.search(currentPos, len, JoniOption.NONE) >= 0) {
                    val byteStart = matcher.getBegin
                    val byteEnd = matcher.getEnd

                    val charStart = byteStart / 2
                    val charEnd = byteEnd / 2

                    val joniRegion = matcher.getRegion
                    val regionCount = if (joniRegion == null) 0 else joniRegion.getNumRegs
                    val subregions = (1 until regionCount).map { i =>
                        val gByteStart = joniRegion.getBeg(i)
                        val gByteEnd = joniRegion.getEnd(i)
                        val gCharStart = if (gByteStart >= 0) gByteStart / 2 else -1
                        val gCharEnd = if (gByteEnd >= 0) gByteEnd / 2 else -1
                        (gCharStart, gCharEnd)
                    }

                    currentPos =
                        if (byteEnd > byteStart) byteEnd
                        else byteStart + 2  // empty match: advance one Java char (= 2 UTF-16 bytes)

                    Some(Location(charStart, charEnd, subregions))
                } else {
                    None
                }
            }
        }

        def locateFirstMatchIn(txt: String): Option[Location] =
            Finder(txt).findMatch()

        def locateAllMatchIn(txt: String): Iterator[Location] = {
            val finder = Finder(txt)
            Iterator.continually(finder.findMatch()).takeWhile(_ != None).flatten
        }

        def replaceAllIn(txt: String, replacement: String): String = ???
    }
}

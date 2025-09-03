/**
  *  Adapter for the Joni engine
  *
  *  This is the most complicated adapter as the Joni engine natively takes UTF-8 rather than UTF-16
  */

package worldofregex

object Joni extends RegexEngine {
    val name="Joni"

    import org.joni.{Regex => JoniRegex, Matcher => JoniMatcher, Option => JoniOption}
    import org.jcodings.specific.UTF8Encoding;

    def compile(pattern: String): Regex = new Regex {
        override def toString=s"Joni($pattern)"

        val engineName="Joni"

        private val pat=pattern.getBytes("UTF-8");
        private val compiledRegex: JoniRegex = new JoniRegex(pat,0,pat.length,JoniOption.NONE, UTF8Encoding.INSTANCE)

        // is there a more idiomatic Joni way to do a WholeMatch than with anchors?   
        private val anchoredRegex = {
            val anchoredPattern=Util.anchorPattern(pattern).getBytes("UTF-8")
            new JoniRegex(anchoredPattern,0,anchoredPattern.length,JoniOption.NONE, UTF8Encoding.INSTANCE)
        }

        /** Checks if the entire string matches the pattern */
        def hasWholeMatch(txt: String): Boolean = {
            val bytes = txt.getBytes("UTF-8")
            val matcher = anchoredRegex.matcher(bytes)
            
            bytes.length == matcher.`match`(0, bytes.length, JoniOption.FIND_LONGEST)
        }

        /** Checks if any substring matches the pattern */
        def hasPartialMatch(txt: String): Boolean = {
            val bytes = txt.getBytes("UTF-8")
            val matcher = compiledRegex.matcher(bytes)
            matcher.search(0, bytes.length, JoniOption.NONE) >= 0
        }

        private case class Finder(txt:String){
            val bytes = txt.getBytes("UTF-8")
            val matcher = compiledRegex.matcher(bytes)
            val len=bytes.length;

            // lazy:  if there is no match, won't need to create the mapping
            lazy val mapping = createByteToCharacterMapping(bytes)


            /* what is the current byte posistion in input */
            var currentPos =0;


            def findMatch():Option[Location]= {
                if (matcher.search(currentPos,len, JoniOption.NONE) >= 0) {
                    val byteStart = matcher.getBegin
                    val byteEnd = matcher.getEnd
                    // Check mapping bounds before access
                    val charStart = if (byteStart >= 0 && byteStart < mapping.length) mapping(byteStart) else -1
                    val charEnd = if (byteEnd >= 0 && byteEnd < mapping.length) mapping(byteEnd) else -1

                    assert(charStart > -1 && charEnd > -1,
                           s"Error: Byte match at ($byteStart, $byteEnd) mapped to char match at ($charStart,$charEnd) for mapping length ${mapping.length} in Joni.findMatch")

                    val joniRegion = matcher.getRegion
                    val regionCount =  if (joniRegion==null) 0 else joniRegion.getNumRegs
                    val subregions = (1 until regionCount).map { i =>
                        val gByteStart = joniRegion.getBeg(i)
                        val gByteEnd = joniRegion.getEnd(i)
                        // Use the precomputed byte-to-char mapping for group positions
                        val gCharStart = if (gByteStart >= 0) mapping(gByteStart) else -1
                        val gCharEnd = if (gByteEnd >= 0) mapping(gByteEnd) else -1
                        (gCharStart, gCharEnd)
                    }
                    currentPos= if (byteEnd>byteStart){
                        // non-empty match 
                        byteEnd;
                    } else {
                        /*  Empty sucessful match.
                         * 
                         *  JavaUtil advances by a 'char' rather than by a codepoint
                         *
                         *  'step' is how many UTF-8 bytes in that char.
                         * 
                         *  bug for bug compatability requires returning 2 empty matches
                         *  for 4 byte UTF-8 characters. 
                         */
                        if (byteStart < bytes.length){

                            val step=(bytes(byteStart) & 0xFF) match {
                                case b if (b & 0x80) == 0 => 1
                                case b if (b & 0xE0) == 0xC0 => 2
                                case b if (b & 0xF0) == 0xE0 => 3
                                case b if (b & 0xF8) == 0xF0 => 2 // first half of empty match on 2 char code point
                                case b if (b & 0xC0) == 0x80 => 2 // second half of empty match on 2 char code point
                                case _ =>
                                    // can't happen
                                    throw new IllegalArgumentException("Invalid UTF-8 encoding")
                            }
                            byteStart + step;
                        } else {
                            byteStart + 1;
                        }
                    }
                    Some(Location(charStart, charEnd, subregions))
                } else {
                    None
                }
            }
        }

        /** Finds the first match in the string, returning its region if found */
        def locateFirstMatchIn(txt: String): Option[Location] = {
            Finder(txt).findMatch()
        }

        /** Returns an iterator over all non-overlapping matches in the string */
        def locateAllMatchIn(txt: String): Iterator[Location] = {
            val finder=Finder(txt)
            Iterator.continually(finder.findMatch()).takeWhile(_ != None).flatten
        }

        def replaceAllIn(txt: String, replacement: String): String= ???
    }

    /* create a mapping from Byte positions in the UTF-8 to character positions in
     * the UTF-16.
     *
     * Complicated because UTF-8 is variable length and as a bonus, so
     * is UTF-16.  When a UTF-8 character 4 bytes, the corresponding
     * UTF-16 'code-point' is 2 Java chars.
     * 
     */
    private def createByteToCharacterMapping(bytes: Array[Byte]): IArray[Int] = {
        // Initialize the mapping array with the same length as the byte array
        // plus 1, so end-of-string can be mapped
        val byteToCharacter = new Array[Int](bytes.length+1)
        var charIndex = 0 // Tracks the current character index

        // Iterate through the byte array
        var i = 0
        while (i < bytes.length) {
            val byteValue = bytes(i)

            //  val byteValue = byte & 0xFF // Convert to unsigned integer

            if ((byteValue & 0x80) == 0) {
                // 1-byte character (ASCII, e.g., 'a')
                byteToCharacter(i) = charIndex
                charIndex += 1
                i += 1
            } else if ((byteValue & 0xE0) == 0xC0) {
                // 2-byte character like 'é'
                if (i + 1 < bytes.length && (bytes(i + 1) & 0xC0) == 0x80) {
                    byteToCharacter(i) = charIndex
                    byteToCharacter(i + 1) = -1
                    charIndex += 1
                    i += 2
                } else {
                    throw new IllegalArgumentException("Invalid UTF-8 encoding at byte index " + i)
                }
            } else if ((byteValue & 0xF0) == 0xE0) {
                // 3-byte character like '€'
                if (i + 2 < bytes.length && (bytes(i + 1) & 0xC0) == 0x80 && (bytes(i + 2) & 0xC0) == 0x80) {
                    byteToCharacter(i) = charIndex
                    byteToCharacter(i + 1) = -1
                    byteToCharacter(i + 2) = -1
                    charIndex += 1
                    i += 3
                } else {
                    throw new IllegalArgumentException("Invalid UTF-8 encoding at byte index " + i)
                }
            } else if ((byteValue & 0xF8) == 0xF0) {
                // 4-byte character (e.g., emoji like 😊)
                if (i + 3 < bytes.length && (bytes(i + 1) & 0xC0) == 0x80 && (bytes(i + 2) & 0xC0) == 0x80 && (bytes(i + 3) & 0xC0) == 0x80) {
                    byteToCharacter(i) = charIndex
                    byteToCharacter(i + 1) = -1
                    byteToCharacter(i + 2) = charIndex+1 // hack
                    byteToCharacter(i + 3) = -1
                    // the code points which use a 2 char UTF-16 encoding are
                    // exactly those with a 4 byte UTF-8 encoding
                    charIndex += 2 
                    i += 4
                } else {
                    throw new IllegalArgumentException("Invalid UTF-8 encoding at byte index " + i)
                }
            } else {
                throw new IllegalArgumentException("Invalid UTF-8 encoding at byte index " + i)
            }
        }
        // The position *after* the last byte maps to the total character count
        if (bytes.length >= 0 && byteToCharacter.length > bytes.length) { // Check bounds
           byteToCharacter(bytes.length) = charIndex
        }

        //tag it as immutable and return
        IArray.unsafeFromArray(byteToCharacter)
    }


}


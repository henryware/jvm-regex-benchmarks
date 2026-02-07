package worldofregex

object Util{

    case class Labeled[T](label:String, item:T) {def apply()=item}

    // doesn't handle recursive functions.  Sad!
    case class Memoize[T:scala.reflect.ClassTag,R](f:Function1[T,R]) extends Function1[T,R] {
        val cache=new scala.collection.mutable.HashMap[T,R];

        def apply(x:T)={
            cache.getOrElseUpdate(x,f(x))
        }
    }

    @annotation.tailrec def doWhile[T](body: =>T)(guard: =>Boolean):T={
        val t:T=body;
        if (!guard){
            t
        } else {
            doWhile(body)(guard)
        }
    }

    extension [A](a: A) {
        inline def |>[B](inline f: A => B): B = f(a)
    }


    /* hack for the DFAs which don't understand these tested items */
    def manglePattern(pat:String)={  
        // all of these are leaky (ie if preceded by a backslash). No matter
        pat.replaceAll("""\\d""","[0-9]").
            replaceAll("""\\D""","[^0-9]").
            replaceAll("""\\s""","""[ \t\n\x0B\f\r]""").
            replaceAll("""\\S""","""[^ \t\n\x0B\f\r]""").
            replaceAll("""\\w""","""[a-zA-Z_0-9]""").
            replaceAll("""\\w""","""[^a-zA-Z_0-9]""").
            replaceAll("""\(\?[:]""","""(""")
    }

    /* Joni and KMY don't always do the right thing on whole matches, but like anchored patterns */
    def anchorPattern(pat:String)={
        s"""^(?:${pat})$$"""
    }

    /* Create a mapping from byte positions in UTF-8 to character positions in UTF-16.
     *
     * Complicated because UTF-8 is variable length and as a bonus, so
     * is UTF-16.  When a UTF-8 character is 4 bytes, the corresponding
     * UTF-16 'code-point' is 2 Java chars.
     */
    def createByteToCharacterMapping(bytes: Array[Byte]): IArray[Int] = {
        val byteToCharacter = new Array[Int](bytes.length + 1)
        var charIndex = 0

        var i = 0
        while (i < bytes.length) {
            val byteValue = bytes(i)

            if ((byteValue & 0x80) == 0) {
                byteToCharacter(i) = charIndex
                charIndex += 1
                i += 1
            } else if ((byteValue & 0xE0) == 0xC0) {
                if (i + 1 < bytes.length && (bytes(i + 1) & 0xC0) == 0x80) {
                    byteToCharacter(i) = charIndex
                    byteToCharacter(i + 1) = -1
                    charIndex += 1
                    i += 2
                } else {
                    throw new IllegalArgumentException("Invalid UTF-8 encoding at byte index " + i)
                }
            } else if ((byteValue & 0xF0) == 0xE0) {
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
                if (i + 3 < bytes.length && (bytes(i + 1) & 0xC0) == 0x80 && (bytes(i + 2) & 0xC0) == 0x80 && (bytes(i + 3) & 0xC0) == 0x80) {
                    byteToCharacter(i) = charIndex
                    byteToCharacter(i + 1) = -1
                    byteToCharacter(i + 2) = charIndex + 1
                    byteToCharacter(i + 3) = -1
                    charIndex += 2
                    i += 4
                } else {
                    throw new IllegalArgumentException("Invalid UTF-8 encoding at byte index " + i)
                }
            } else {
                throw new IllegalArgumentException("Invalid UTF-8 encoding at byte index " + i)
            }
        }
        if (bytes.length >= 0 && byteToCharacter.length > bytes.length) {
            byteToCharacter(bytes.length) = charIndex
        }

        IArray.unsafeFromArray(byteToCharacter)
    }
}

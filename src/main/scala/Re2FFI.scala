/**
  * Adapter for native RE2 via Panama FFI (java.lang.foreign)
  *
  * Uses a thin C shim (lib/libre2_c.so) around the C++ RE2 library.
  * Operates on UTF-8 bytes, converting byte offsets to char offsets.
  */

package worldofregex

import java.lang.foreign._
import java.lang.foreign.ValueLayout._
import java.lang.invoke.MethodHandle

object Re2FFI extends RegexEngine {
    val name = "Re2FFI"

    private val linker = Linker.nativeLinker()
    private val lib = SymbolLookup.libraryLookup(
        java.nio.file.Path.of("lib/libre2_c.so").toAbsolutePath, Arena.global()
    )

    // const char *re2c_version(void)
    private val re2c_version_h: MethodHandle = linker.downcallHandle(
        lib.find("re2c_version").orElseThrow(() => new RuntimeException("Symbol not found: re2c_version")),
        FunctionDescriptor.of(ADDRESS)
    )

    lazy val version: String = {
        val seg = re2c_version_h.invoke().asInstanceOf[MemorySegment]
        if (seg == MemorySegment.NULL || seg.address() == 0) ""
        else seg.reinterpret(256).getString(0)
    }

    private def lookup(name: String): MemorySegment =
        lib.find(name).orElseThrow(() => new RuntimeException(s"Symbol not found: $name"))

    // void *re2c_compile(const char *pattern)
    private val re2c_compile: MethodHandle = linker.downcallHandle(
        lookup("re2c_compile"),
        FunctionDescriptor.of(ADDRESS, ADDRESS)
    )

    // void re2c_free(void *re)
    private val re2c_free: MethodHandle = linker.downcallHandle(
        lookup("re2c_free"),
        FunctionDescriptor.ofVoid(ADDRESS)
    )

    // int re2c_ok(void *re)
    private val re2c_ok: MethodHandle = linker.downcallHandle(
        lookup("re2c_ok"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS)
    )

    // const char *re2c_error(void *re)
    private val re2c_error: MethodHandle = linker.downcallHandle(
        lookup("re2c_error"),
        FunctionDescriptor.of(ADDRESS, ADDRESS)
    )

    // int re2c_num_groups(void *re)
    private val re2c_num_groups: MethodHandle = linker.downcallHandle(
        lookup("re2c_num_groups"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS)
    )

    // int re2c_find(void *re, const char *text, int text_len,
    //               int start, int anchor, int *positions, int max_pairs)
    private val re2c_find: MethodHandle = linker.downcallHandle(
        lookup("re2c_find"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT)
    )

    private val UNANCHORED = 0
    private val ANCHOR_BOTH = 2

    def compile(pattern: String): Regex = {
        val arena = Arena.ofShared()
        val patSeg = arena.allocateFrom(pattern)

        val re = re2c_compile.invoke(patSeg).asInstanceOf[MemorySegment]
        if (re == MemorySegment.NULL || re.address() == 0)
            throw new RegexException(s"RE2 compile returned null for '$pattern'", null)

        val ok = re2c_ok.invoke(re).asInstanceOf[Int]
        if (ok == 0) {
            val errSeg = re2c_error.invoke(re).asInstanceOf[MemorySegment]
            val errMsg = errSeg.reinterpret(256).getString(0)
            re2c_free.invoke(re)
            arena.close()
            throw new RegexException(s"RE2 compile failed for '$pattern': $errMsg", null)
        }

        val numGroups = re2c_num_groups.invoke(re).asInstanceOf[Int]
        val totalPairs = numGroups + 1 // group 0 + capturing groups

        new Regex {
            override def toString = s"Re2FFI($pattern)"
            def engineName = "Re2FFI"

            private def doFind(bytes: Array[Byte], startByte: Int, anchor: Int,
                               a: Arena): Option[(IArray[Int], Int)] = {
                val sub = a.allocate(math.max(bytes.length.toLong, 1L))
                if (bytes.length > 0)
                    MemorySegment.copy(bytes, 0, sub, JAVA_BYTE, 0, bytes.length)
                val posSeg = a.allocate(JAVA_INT, (totalPairs * 2).toLong)
                val rc = re2c_find.invoke(
                    re, sub, bytes.length, startByte, anchor, posSeg, totalPairs
                ).asInstanceOf[Int]
                if (rc <= 0) None
                else {
                    val positions = new Array[Int](rc * 2)
                    for (i <- 0 until rc * 2)
                        positions(i) = posSeg.getAtIndex(JAVA_INT, i.toLong)
                    Some((IArray.unsafeFromArray(positions), rc))
                }
            }

            def hasWholeMatch(txt: String): Boolean = {
                val bytes = txt.getBytes("UTF-8")
                val a = Arena.ofConfined()
                try {
                    doFind(bytes, 0, ANCHOR_BOTH, a).isDefined
                } finally {
                    a.close()
                }
            }

            def hasPartialMatch(txt: String): Boolean = {
                val bytes = txt.getBytes("UTF-8")
                val a = Arena.ofConfined()
                try {
                    doFind(bytes, 0, UNANCHORED, a).isDefined
                } finally {
                    a.close()
                }
            }

            private case class Finder(txt: String) {
                val bytes = txt.getBytes("UTF-8")
                val len = bytes.length
                lazy val mapping = Util.createByteToCharacterMapping(bytes)
                var currentBytePos = 0
                var pendingEmptyMatch: Option[Location] = None

                def findMatch(a: Arena): Option[Location] = {
                    if (pendingEmptyMatch.isDefined) {
                        val r = pendingEmptyMatch
                        pendingEmptyMatch = None
                        return r
                    }
                    if (currentBytePos > len) return None

                    doFind(bytes, currentBytePos, UNANCHORED, a) match {
                        case None => None
                        case Some((positions, rc)) =>
                            val byteStart = positions(0)
                            val byteEnd = positions(1)
                            val charStart = mapping(byteStart)
                            val charEnd = mapping(byteEnd)

                            val subregions = (1 until rc).map { i =>
                                val gByteStart = positions(i * 2)
                                val gByteEnd = positions(i * 2 + 1)
                                if (gByteStart < 0) (-1, -1)
                                else (mapping(gByteStart), mapping(gByteEnd))
                            }

                            currentBytePos = if (byteEnd > byteStart) {
                                byteEnd
                            } else {
                                if (byteStart < bytes.length) {
                                    val b = bytes(byteStart) & 0xFF
                                    if ((b & 0x80) == 0) byteStart + 1
                                    else if ((b & 0xE0) == 0xC0) byteStart + 2
                                    else if ((b & 0xF0) == 0xE0) byteStart + 3
                                    else if ((b & 0xF8) == 0xF0) {
                                        pendingEmptyMatch = Some(Location(charStart + 1, charEnd + 1))
                                        byteStart + 4
                                    } else byteStart + 1
                                } else {
                                    byteStart + 1
                                }
                            }
                            Some(Location(charStart, charEnd, subregions))
                    }
                }
            }

            def locateFirstMatchIn(txt: String): Option[Location] = {
                val a = Arena.ofConfined()
                try {
                    Finder(txt).findMatch(a)
                } finally {
                    a.close()
                }
            }

            def locateAllMatchIn(txt: String): Iterator[Location] = {
                val a = Arena.ofConfined()
                val finder = Finder(txt)
                new Iterator[Location] {
                    private var nextLoc: Option[Location] = finder.findMatch(a)

                    def hasNext: Boolean = nextLoc.isDefined

                    def next(): Location = {
                        val result = nextLoc.get
                        nextLoc = finder.findMatch(a)
                        if (nextLoc.isEmpty) a.close()
                        result
                    }
                }
            }
        }
    }
}

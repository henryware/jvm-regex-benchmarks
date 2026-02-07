/**
  * Adapter for PCRE2 via Panama FFI (java.lang.foreign)
  *
  * Uses libpcre2-8 with JIT compilation for speed.
  * Operates on UTF-8 bytes, converting byte offsets to char offsets.
  */

package worldofregex

import java.lang.foreign._
import java.lang.foreign.ValueLayout._
import java.lang.invoke.MethodHandle

object Pcre2 extends RegexEngine {
    val name = "Pcre2"

    private val linker = Linker.nativeLinker()
    private val lib = SymbolLookup.libraryLookup("libpcre2-8.so", Arena.global())

    private def lookup(name: String): MemorySegment =
        lib.find(name).orElseThrow(() => new RuntimeException(s"Symbol not found: $name"))

    // pcre2_code_8 *pcre2_compile_8(const uint8_t *pattern, size_t length,
    //   uint32_t options, int *errorcode, size_t *erroroffset,
    //   pcre2_compile_context_8 *ccontext)
    private val pcre2_compile: MethodHandle = linker.downcallHandle(
        lookup("pcre2_compile_8"),
        FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
    )

    // void pcre2_code_free_8(pcre2_code_8 *code)
    private val pcre2_code_free: MethodHandle = linker.downcallHandle(
        lookup("pcre2_code_free_8"),
        FunctionDescriptor.ofVoid(ADDRESS)
    )

    // int pcre2_jit_compile_8(pcre2_code_8 *code, uint32_t options)
    private val pcre2_jit_compile: MethodHandle = linker.downcallHandle(
        lookup("pcre2_jit_compile_8"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)
    )

    // pcre2_match_data_8 *pcre2_match_data_create_from_pattern_8(
    //   const pcre2_code_8 *code, pcre2_general_context_8 *gcontext)
    private val pcre2_match_data_create_from_pattern: MethodHandle = linker.downcallHandle(
        lookup("pcre2_match_data_create_from_pattern_8"),
        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS)
    )

    // void pcre2_match_data_free_8(pcre2_match_data_8 *match_data)
    private val pcre2_match_data_free: MethodHandle = linker.downcallHandle(
        lookup("pcre2_match_data_free_8"),
        FunctionDescriptor.ofVoid(ADDRESS)
    )

    // int pcre2_match_8(const pcre2_code_8 *code, const uint8_t *subject,
    //   size_t length, size_t startoffset, uint32_t options,
    //   pcre2_match_data_8 *match_data, pcre2_match_context_8 *mcontext)
    private val pcre2_match: MethodHandle = linker.downcallHandle(
        lookup("pcre2_match_8"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS)
    )

    // size_t *pcre2_get_ovector_pointer_8(pcre2_match_data_8 *match_data)
    private val pcre2_get_ovector_pointer: MethodHandle = linker.downcallHandle(
        lookup("pcre2_get_ovector_pointer_8"),
        FunctionDescriptor.of(ADDRESS, ADDRESS)
    )

    // uint32_t pcre2_get_ovector_count_8(pcre2_match_data_8 *match_data)
    private val pcre2_get_ovector_count: MethodHandle = linker.downcallHandle(
        lookup("pcre2_get_ovector_count_8"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS)
    )

    private val PCRE2_ANCHORED = 0x80000000
    private val PCRE2_ENDANCHORED = 0x20000000
    private val PCRE2_JIT_COMPLETE = 0x00000001
    private val PCRE2_UTF = 0x00080000
    private val PCRE2_ERROR_NOMATCH = -1
    private val PCRE2_UNSET = -1L // ~(size_t)0 but we check as signed

    def compile(pattern: String): Regex = {
        val arena = Arena.ofShared()
        val patBytes = pattern.getBytes("UTF-8")
        val patSeg = arena.allocate(patBytes.length.toLong + 1)
        MemorySegment.copy(patBytes, 0, patSeg, JAVA_BYTE, 0, patBytes.length)
        patSeg.set(JAVA_BYTE, patBytes.length.toLong, 0.toByte)

        val errCode = arena.allocate(JAVA_INT)
        val errOffset = arena.allocate(JAVA_LONG)

        val code = pcre2_compile.invoke(
            patSeg, patBytes.length.toLong, PCRE2_UTF,
            errCode, errOffset, MemorySegment.NULL
        ).asInstanceOf[MemorySegment]

        if (code == MemorySegment.NULL || code.address() == 0) {
            arena.close()
            throw new RegexException(
                s"PCRE2 compile failed for '$pattern' at offset ${errOffset.get(JAVA_LONG, 0)}, error ${errCode.get(JAVA_INT, 0)}",
                null
            )
        }

        // JIT compile — ignore failure (falls back to interpreter)
        pcre2_jit_compile.invoke(code, PCRE2_JIT_COMPLETE)

        new Regex {
            override def toString = s"Pcre2($pattern)"
            def engineName = "Pcre2"

            def hasWholeMatch(txt: String): Boolean = {
                val bytes = txt.getBytes("UTF-8")
                val a = Arena.ofConfined()
                try {
                    val sub = a.allocate(bytes.length.toLong)
                    MemorySegment.copy(bytes, 0, sub, JAVA_BYTE, 0, bytes.length)
                    val md = pcre2_match_data_create_from_pattern.invoke(code, MemorySegment.NULL)
                        .asInstanceOf[MemorySegment]
                    try {
                        val rc = pcre2_match.invoke(
                            code, sub, bytes.length.toLong, 0L,
                            PCRE2_ANCHORED | PCRE2_ENDANCHORED, md, MemorySegment.NULL
                        ).asInstanceOf[Int]
                        rc >= 0
                    } finally {
                        pcre2_match_data_free.invoke(md)
                    }
                } finally {
                    a.close()
                }
            }

            def hasPartialMatch(txt: String): Boolean = {
                val bytes = txt.getBytes("UTF-8")
                val a = Arena.ofConfined()
                try {
                    val sub = a.allocate(bytes.length.toLong)
                    MemorySegment.copy(bytes, 0, sub, JAVA_BYTE, 0, bytes.length)
                    val md = pcre2_match_data_create_from_pattern.invoke(code, MemorySegment.NULL)
                        .asInstanceOf[MemorySegment]
                    try {
                        val rc = pcre2_match.invoke(
                            code, sub, bytes.length.toLong, 0L,
                            0, md, MemorySegment.NULL
                        ).asInstanceOf[Int]
                        rc >= 0
                    } finally {
                        pcre2_match_data_free.invoke(md)
                    }
                } finally {
                    a.close()
                }
            }

            private case class Finder(txt: String) {
                val bytes = txt.getBytes("UTF-8")
                val len = bytes.length
                lazy val mapping = Util.createByteToCharacterMapping(bytes)
                var currentBytePos = 0
                // Queue for synthetic empty matches at the second surrogate half
                // of 4-byte UTF-8 chars (JavaUtil bug-for-bug compat)
                var pendingEmptyMatch: Option[Location] = None

                def findMatch(a: Arena): Option[Location] = {
                    // Return queued surrogate-half empty match first
                    if (pendingEmptyMatch.isDefined) {
                        val r = pendingEmptyMatch
                        pendingEmptyMatch = None
                        return r
                    }
                    if (currentBytePos > len) return None
                    val sub = a.allocate(len.toLong)
                    MemorySegment.copy(bytes, 0, sub, JAVA_BYTE, 0, len)
                    val md = pcre2_match_data_create_from_pattern.invoke(code, MemorySegment.NULL)
                        .asInstanceOf[MemorySegment]
                    try {
                        val rc = pcre2_match.invoke(
                            code, sub, len.toLong, currentBytePos.toLong,
                            0, md, MemorySegment.NULL
                        ).asInstanceOf[Int]
                        if (rc < 0) {
                            None
                        } else {
                            val ovecRaw = pcre2_get_ovector_pointer.invoke(md).asInstanceOf[MemorySegment]
                            val ovecCount = pcre2_get_ovector_count.invoke(md).asInstanceOf[Int]
                            val ovecPtr = ovecRaw.reinterpret(8L * 2 * ovecCount.toLong)

                            val byteStart = ovecPtr.get(JAVA_LONG, 0).toInt
                            val byteEnd = ovecPtr.get(JAVA_LONG, 8).toInt
                            val charStart = mapping(byteStart)
                            val charEnd = mapping(byteEnd)

                            val subregions = (1 until ovecCount).map { i =>
                                val gByteStart = ovecPtr.get(JAVA_LONG, (i * 2).toLong * 8)
                                val gByteEnd = ovecPtr.get(JAVA_LONG, (i * 2 + 1).toLong * 8)
                                if (gByteStart == PCRE2_UNSET || gByteStart < 0) {
                                    (-1, -1)
                                } else {
                                    (mapping(gByteStart.toInt), mapping(gByteEnd.toInt))
                                }
                            }

                            currentBytePos = if (byteEnd > byteStart) {
                                byteEnd
                            } else {
                                // Empty match advance. PCRE2 in UTF mode requires
                                // start offsets at codepoint boundaries, so we must
                                // advance by full codepoints. For 4-byte UTF-8 chars
                                // (which map to 2 UTF-16 chars / surrogate pair), we
                                // queue a synthetic second empty match for JavaUtil
                                // bug-for-bug compat.
                                if (byteStart < bytes.length) {
                                    val b = bytes(byteStart) & 0xFF
                                    if ((b & 0x80) == 0) {
                                        byteStart + 1
                                    } else if ((b & 0xE0) == 0xC0) {
                                        byteStart + 2
                                    } else if ((b & 0xF0) == 0xE0) {
                                        byteStart + 3
                                    } else if ((b & 0xF8) == 0xF0) {
                                        // 4-byte char = surrogate pair in UTF-16
                                        // Queue empty match for second surrogate half
                                        pendingEmptyMatch = Some(Location(charStart + 1, charEnd + 1))
                                        byteStart + 4
                                    } else {
                                        byteStart + 1
                                    }
                                } else {
                                    byteStart + 1
                                }
                            }
                            Some(Location(charStart, charEnd, subregions))
                        }
                    } finally {
                        pcre2_match_data_free.invoke(md)
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

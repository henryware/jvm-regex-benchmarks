/**
  * Adapter for Hyperscan via Panama FFI (java.lang.foreign)
  *
  * Uses libhs in block mode with HS_FLAG_SOM_LEFTMOST for start-of-match.
  * Callback-based matching via Linker.upcallStub().
  * No capture group support (like DkBrics/MonqJFA).
  *
  * Hyperscan is a streaming-oriented DFA engine. It reports match end-offsets
  * to callbacks. With HS_FLAG_SOM_LEFTMOST it also reports start offsets, but
  * this flag is incompatible with patterns that can match empty strings. In
  * those cases we fall back to no-SOM mode. The locate* methods may give
  * different empty-match iteration than other DFAs because Hyperscan reports
  * all match endpoints, not just greedy/non-overlapping ones.
  */

package worldofregex

import java.lang.foreign._
import java.lang.foreign.ValueLayout._
import java.lang.invoke.{MethodHandle, MethodHandles, MethodType}
import worldofregex.Util.manglePattern

object Hyperscan extends RegexEngine {
    val name = "Hyperscan"

    private val linker = Linker.nativeLinker()
    private val lib = SymbolLookup.libraryLookup("libhs.so", Arena.global())

    private def lookup(name: String): MemorySegment =
        lib.find(name).orElseThrow(() => new RuntimeException(s"Symbol not found: $name"))

    private val hs_compile_h: MethodHandle = linker.downcallHandle(
        lookup("hs_compile"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
    )

    private val hs_free_database: MethodHandle = linker.downcallHandle(
        lookup("hs_free_database"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS)
    )

    private val hs_alloc_scratch: MethodHandle = linker.downcallHandle(
        lookup("hs_alloc_scratch"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)
    )

    private val hs_free_scratch: MethodHandle = linker.downcallHandle(
        lookup("hs_free_scratch"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS)
    )

    private val hs_scan_h: MethodHandle = linker.downcallHandle(
        lookup("hs_scan"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
    )

    private val hs_free_compile_error: MethodHandle = linker.downcallHandle(
        lookup("hs_free_compile_error"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS)
    )

    private val HS_FLAG_SOM_LEFTMOST = 256
    private val HS_FLAG_ALLOWEMPTY = 16
    private val HS_FLAG_UTF8 = 32
    private val HS_FLAG_DOTALL = 2
    private val HS_MODE_BLOCK = 1
    private val HS_SUCCESS = 0

    // callback: int(unsigned int id, unsigned long long from,
    //   unsigned long long to, unsigned int flags, void *context)
    private val CALLBACK_DESC = FunctionDescriptor.of(
        JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT, ADDRESS
    )

    private def hsCompile(arena: Arena, pat: String, flags: Int): (MemorySegment, Boolean) = {
        val patSeg = arena.allocateFrom(pat)
        val dbPtr = arena.allocate(ADDRESS)
        val errPtr = arena.allocate(ADDRESS)
        val rc = hs_compile_h.invoke(
            patSeg, flags, HS_MODE_BLOCK,
            MemorySegment.NULL, dbPtr, errPtr
        ).asInstanceOf[Int]
        if (rc != HS_SUCCESS) {
            val errSeg = errPtr.get(ADDRESS, 0)
            hs_free_compile_error.invoke(errSeg)
            (MemorySegment.NULL, false)
        } else {
            (dbPtr.get(ADDRESS, 0), true)
        }
    }

    private def allocScratch(arena: Arena, db: MemorySegment): MemorySegment = {
        val scratchPtr = arena.allocate(ADDRESS)
        scratchPtr.set(ADDRESS, 0, MemorySegment.NULL)
        val rc = hs_alloc_scratch.invoke(db, scratchPtr).asInstanceOf[Int]
        if (rc != HS_SUCCESS)
            throw new RegexException("Hyperscan scratch alloc failed", null)
        scratchPtr.get(ADDRESS, 0)
    }

    def compile(pattern: String): Regex = {
        val mangledPattern = manglePattern(pattern)
        val arena = Arena.ofShared()

        val baseFlags = HS_FLAG_ALLOWEMPTY | HS_FLAG_UTF8 | HS_FLAG_DOTALL

        // Main DB with SOM_LEFTMOST if possible, fallback without
        val (db, hasSom) = {
            val (d, ok) = hsCompile(arena, mangledPattern, baseFlags | HS_FLAG_SOM_LEFTMOST)
            if (ok) (d, true)
            else {
                val (d2, ok2) = hsCompile(arena, mangledPattern, baseFlags)
                if (ok2) (d2, false)
                else {
                    arena.close()
                    throw new RegexException(
                        s"Hyperscan compile failed for '$pattern' (mangled: '$mangledPattern')", null
                    )
                }
            }
        }

        // Separate anchored DB for whole matching: ^(?:pattern)$
        // Hyperscan uses ^ and $ literally when in block mode
        val anchoredPat = s"^(?:$mangledPattern)$$"
        val (anchoredDb, _) = {
            val (d, ok) = hsCompile(arena, anchoredPat, baseFlags)
            if (ok) (d, true)
            else {
                // if anchored compile fails, fall back to main db
                (db, false)
            }
        }

        new Regex {
            override def toString = s"Hyperscan($pattern)"
            def engineName = "Hyperscan"

            private def doScan(localScratch: MemorySegment, targetDb: MemorySegment,
                               dataSeg: MemorySegment, dataLen: Int, stub: MemorySegment): Unit = {
                hs_scan_h.invoke(targetDb, dataSeg, dataLen, 0, localScratch, stub, MemorySegment.NULL)
                ()
            }

            private def withScratch(targetDb: MemorySegment)(f: MemorySegment => Unit): Unit = {
                val localArena = Arena.ofConfined()
                try {
                    val localScratch = allocScratch(localArena, targetDb)
                    try {
                        f(localScratch)
                    } finally {
                        hs_free_scratch.invoke(localScratch)
                    }
                } finally {
                    localArena.close()
                }
            }

            private def allocData(a: Arena, bytes: Array[Byte]): MemorySegment = {
                if (bytes.length > 0) {
                    val s = a.allocate(bytes.length.toLong)
                    MemorySegment.copy(bytes, 0, s, JAVA_BYTE, 0, bytes.length)
                    s
                } else {
                    a.allocate(1L)
                }
            }

            def hasWholeMatch(txt: String): Boolean = {
                val bytes = txt.getBytes("UTF-8")
                var matched = false
                withScratch(anchoredDb) { localScratch =>
                    val a = Arena.ofConfined()
                    try {
                        val dataSeg = allocData(a, bytes)
                        val stub = makeCallback(a) { (_, _, _, _, _) =>
                            matched = true; 1
                        }
                        doScan(localScratch, anchoredDb, dataSeg, bytes.length, stub)
                    } finally {
                        a.close()
                    }
                }
                matched
            }

            def hasPartialMatch(txt: String): Boolean = {
                val bytes = txt.getBytes("UTF-8")
                var matched = false
                withScratch(db) { localScratch =>
                    val a = Arena.ofConfined()
                    try {
                        val dataSeg = allocData(a, bytes)
                        val stub = makeCallback(a) { (_, _, _, _, _) =>
                            matched = true; 1
                        }
                        doScan(localScratch, db, dataSeg, bytes.length, stub)
                    } finally {
                        a.close()
                    }
                }
                matched
            }

            def locateFirstMatchIn(txt: String): Option[Location] = {
                val bytes = txt.getBytes("UTF-8")
                lazy val mapping = Util.createByteToCharacterMapping(bytes)
                var result: Option[Location] = None
                withScratch(db) { localScratch =>
                    val a = Arena.ofConfined()
                    try {
                        val dataSeg = allocData(a, bytes)
                        if (hasSom) {
                            val stub = makeCallback(a) { (_, from, to, _, _) =>
                                result = Some(Location(mapping(from.toInt), mapping(to.toInt)))
                                1
                            }
                            doScan(localScratch, db, dataSeg, bytes.length, stub)
                        } else {
                            val stub = makeCallback(a) { (_, _, to, _, _) =>
                                result = Some(Location(0, mapping(to.toInt)))
                                1
                            }
                            doScan(localScratch, db, dataSeg, bytes.length, stub)
                        }
                    } finally {
                        a.close()
                    }
                }
                result
            }

            def locateAllMatchIn(txt: String): Iterator[Location] = {
                val bytes = txt.getBytes("UTF-8")
                lazy val mapping = Util.createByteToCharacterMapping(bytes)
                val matches = scala.collection.mutable.ArrayBuffer[Location]()
                withScratch(db) { localScratch =>
                    val a = Arena.ofConfined()
                    try {
                        val dataSeg = allocData(a, bytes)
                        if (hasSom) {
                            val stub = makeCallback(a) { (_, from, to, _, _) =>
                                matches += Location(mapping(from.toInt), mapping(to.toInt))
                                0
                            }
                            doScan(localScratch, db, dataSeg, bytes.length, stub)
                        } else {
                            val stub = makeCallback(a) { (_, _, to, _, _) =>
                                matches += Location(0, mapping(to.toInt))
                                0
                            }
                            doScan(localScratch, db, dataSeg, bytes.length, stub)
                        }
                    } finally {
                        a.close()
                    }
                }
                matches.iterator
            }

            private def makeCallback(a: Arena)(
                handler: (Int, Long, Long, Int, MemorySegment) => Int
            ): MemorySegment = {
                val mh = MethodHandles.lookup().bind(
                    new CallbackTarget(handler),
                    "invoke",
                    MethodType.methodType(classOf[Int], classOf[Int], classOf[Long], classOf[Long], classOf[Int], classOf[MemorySegment])
                )
                linker.upcallStub(mh, CALLBACK_DESC, a)
            }
        }
    }

    private class CallbackTarget(handler: (Int, Long, Long, Int, MemorySegment) => Int) {
        def invoke(id: Int, from: Long, to: Long, flags: Int, context: MemorySegment): Int = {
            handler(id, from, to, flags, context)
        }
    }
}

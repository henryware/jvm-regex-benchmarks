package worldofregex.macros

import com.justinblank.strings.{Matcher, Pattern}

import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

object NeedleRuntime {

    private val cache = new ConcurrentHashMap[String, Pattern]()

    def materialize(name: String, chunks: String*): Pattern =
        cache.computeIfAbsent(name, n => define(n, chunks))

    private def define(name: String, chunks: Seq[String]): Pattern = {
        val bytes = Base64.getDecoder.decode(joinAll(chunks))
        val loader = new BytesClassLoader(classOf[Pattern].getClassLoader)
        val cls = loader.define(name, bytes).asInstanceOf[Class[? <: Matcher]]
        new CompiledPattern(cls)
    }

    private def joinAll(chunks: Seq[String]): String = {
        if (chunks.length == 1) chunks.head
        else {
            val sb = new java.lang.StringBuilder(chunks.iterator.map(_.length).sum)
            chunks.foreach(sb.append)
            sb.toString
        }
    }

    private final class BytesClassLoader(parent: ClassLoader) extends ClassLoader(parent) {
        def define(name: String, bytes: Array[Byte]): Class[?] =
            defineClass(name, bytes, 0, bytes.length)
    }

    private final class CompiledPattern(matcherClass: Class[? <: Matcher]) extends Pattern {
        private val ctor = matcherClass.getDeclaredConstructor(classOf[String])
        def matcher(s: String): Matcher = ctor.newInstance(s)
    }
}

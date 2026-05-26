package worldofregex.macros

import com.justinblank.strings.{DFACompiler, Pattern}

import java.security.MessageDigest
import java.util.Base64
import scala.quoted.*

object NeedleMacro {

    def compileImpl(regex: Expr[String])(using Quotes): Expr[Pattern] = {
        import quotes.reflect.*
        val literal = regex.value.getOrElse {
            report.errorAndAbort(
                "needle: regex must be a compile-time constant (a String literal or `inline val`)",
                regex
            )
        }
        val name = syntheticName(literal)
        val bytes =
            try DFACompiler.compileToBytes(literal, name)
            catch {
                case t: Throwable =>
                    report.errorAndAbort(s"needle: failed to compile regex `$literal`: ${t.getMessage}")
            }
        val chunks = chunked(Base64.getEncoder.encodeToString(bytes), MaxChunkChars)
        '{ NeedleRuntime.materialize(${ Expr(name) }, ${ Varargs(chunks.map(Expr(_))) }*) }
    }

    // Stay under the 65535-byte CONSTANT_Utf8 limit. Base64 output is ASCII (1 byte/char),
    // so 60000 chars leaves comfortable headroom.
    private inline val MaxChunkChars = 60000

    private def chunked(s: String, n: Int): Seq[String] = {
        if (s.length <= n) Seq(s)
        else {
            val builder = Seq.newBuilder[String]
            var i = 0
            while (i < s.length) {
                val end = math.min(i + n, s.length)
                builder += s.substring(i, end)
                i = end
            }
            builder.result()
        }
    }

    private def syntheticName(regex: String): String = {
        val digest = MessageDigest.getInstance("SHA-1").digest(regex.getBytes("UTF-8"))
        val sb = new StringBuilder(16)
        var i = 0
        while (i < 8) {
            sb.append(f"${digest(i) & 0xff}%02x")
            i += 1
        }
        s"Needle$$Compiled$$${sb.toString}"
    }
}

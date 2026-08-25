
package worldofregex {
    import org.scalacheck._
    import org.scalacheck.Gen
    import org.scalacheck.Prop.{forAll,propBoolean}
    import org.scalacheck.Arbitrary.arbitrary
    import wolfendale.scalacheck.regexp.RegexpGen
    import Util.|>

    object Constants{
        // after http://tusker.org/regex/regtest.java and
        // https://www.javaadvent.com/2015/12/java-regular-expression-library-benchmarks-2015.html
        private val URL_MATCH1 = """([^:]+://)?([^:/]+)(:([0-9]+))?(/.*)"""
        private val URL_MATCH2 = """(([^:]+)://)?([^:/]+)(:([0-9]+))?(/.*)"""
        private val USD_MATCH = "usd [-+]?[0-9]+.[0-9][0-9]"  
        val LONG_MATCH = """\{(\d+):.*\}""";

        val PATTERNS=List( URL_MATCH1, URL_MATCH2, USD_MATCH, LONG_MATCH)

        val TEXTS= List("http://www.linux.com/",
                        "http://www.thelinuxshow.com/main.php3",
                        "usd 1234.00",
                        "he said she said he said no" ,
                        "same same same",
                        "{1:\n",
                        "this is some more text - and some more and some more and even more\n" * 42 +  "-}\n"
        )
        val VERY_LONG_TEXT= "this is some more text - and some more and some more and even more (can you believe it!) "*200000 + "(650) 253-0001."


        val LONG_TEXT= Util.Memoize{ (i:Int) =>
            val n= 1<<(i);
            Gen.stringOfN(n,Gen.asciiPrintableChar).sample.get
        }

        // slightly shorter to make room for a phone number
        val ALMOST_LONG_TEXT= Util.Memoize{ (i:Int) =>
            val len=1<<(i)
            val n=if (len >=15) len-15 else 0;
            Gen.stringOfN(n,Gen.asciiPrintableChar).sample.get
        }

        val AB= Util.Memoize{ (i:Int) =>
            val n= 1<<(i);
            // string has 19b's per a
            s"a${Gen.stringOfN(n-1,Gen.frequency((1,'a'),(19, 'b'))).sample.get}";
        }

        val AXpat= "a(.*X)?"

        val AAA= Util.Memoize{ (i:Int) =>
            "a" * i;
        }

        val AAApat= Util.Memoize{ (i:Int) =>
            s"""${"a?" * i}${"a" * i}""";
        }


        val LONG_TEXT_PN= Util.Memoize{ (i:Int) =>
            s"""${ALMOST_LONG_TEXT(i)}(650) 253-0001."""
        }

        // Non-ASCII corpus: CJK Unified Ideographs (3 bytes/char in UTF-8,
        // 2 bytes/char in UTF-16).  Implementations/drivers have tradeoffs, lets see how they played out
        val LONG_UNICODE_TEXT= Util.Memoize{ (i:Int) =>
            val n= 1<<(i)
            Gen.stringOfN(n, Gen.choose(0x4e00, 0x9fff).map(_.toChar)).sample.get
        }

        val ALMOST_LONG_UNICODE_TEXT= Util.Memoize{ (i:Int) =>
            val len=1<<(i)
            val n=if (len >=15) len-15 else 0
            Gen.stringOfN(n, Gen.choose(0x4e00, 0x9fff).map(_.toChar)).sample.get
        }

        val LONG_UNICODE_TEXT_PN= Util.Memoize{ (i:Int) =>
            s"""${ALMOST_LONG_UNICODE_TEXT(i)}(650) 253-0001."""
        }

        // A decently complicated pattern over CJK text: a run of ideographs
        // followed by a multi-char "company type" marker and an optional unit
        // suffix.  Literal chars (no \u escapes) so the DFA engines accept it.
        // The greedy [一-鿿]{2,8} overlaps the markers, forcing backtracking
        // engines to back off where the DFAs find the match in one pass.
        val CJK_LOCATE_PAT= """[一-鿿]{2,8}(?:株式会社|有限公司|股份有限公司)(?:第[0-9一-鿿]{1,3}号)?"""

        // Embedded target that CJK_LOCATE_PAT matches: "東京" + "株式会社" + "第一号".
        private val CJK_TARGET= "東京株式会社第一号"

        val LONG_UNICODE_TEXT_CJK= Util.Memoize{ (i:Int) =>
            s"""${ALMOST_LONG_UNICODE_TEXT(i)}$CJK_TARGET"""
        }

        val ABCpat= """[ -~]*ABCDEFGHIJKLMNZ""";

        val LONG_TEXT_ABC= Util.Memoize{ (i:Int) =>
            s"""${ALMOST_LONG_TEXT(i)}ABCDEFGHIJKLMNZ""""
        }

        val PHONE_NUM="""(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})""";

        val PARSE_PHONE_NUM="""\(?(\d{3})\)?\s?-\s{0,2}(\d{3})-(\d{4})""";


        lazy val dictionary={
            scala.io.Source.fromResource("words.txt").getLines().toArray
        }

        lazy val words={
            dictionary.filter(_.size==9).toList |>
                scala.util.Random.shuffle
        }

        // Kernighan & Pike, The Practice of Programming: words whose
        // vowels are exactly a,e,i,o,u in that order.  Original is
        // ^...$; anchors omitted so DFA engines that reject them still
        // run.  hasWholeMatch supplies the same whole-line semantics.
        val ALPHAVOWELS="""[^aeiou]*a[^aeiou]*e[^aeiou]*i[^aeiou]*o[^aeiou]*u[^aeiou]*"""

        private def repeatToLength(i:Int, lineAt:Int=>String):Array[String]={
            val target= 1<<(i)
            val buf= Array.newBuilder[String]
            var n=0
            var k=0
            while (n < target) {
                val line= lineAt(k)
                buf += line
                n += line.length
                k += 1
            }
            buf.result()
        }

        val DICT_LINES= Util.Memoize{ (i:Int) =>
            val src= dictionary
            val len= src.length
            repeatToLength(i, k => src(k % len))
        }

        // Synthetic ~120-char access-log line.  Anchors omitted; hasWholeMatch
        // is the whole-line check.  Hyphen is first in classes so Monq/Needle
        // do not treat "_-" as a range.  No [^class]* so JiTrex can compile it.
        val ACCESSLOG="""[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}[.][0-9]{3}Z (INFO|WARN|ERROR) [-A-Za-z0-9_]+ [A-Z]+ /[-A-Za-z0-9/_]+ [0-9]{3} [0-9]+ms [0-9]{1,3}[.][0-9]{1,3}[.][0-9]{1,3}[.][0-9]{1,3}"""

        private val accessLogTargetLen=120
        private val accessLogLevels=Array("INFO","WARN","ERROR")
        private val accessLogServices=Array("api-gateway","worker-12","auth","inventory","checkout")
        private val accessLogMethods=Array("GET","POST","PUT","DELETE","HEAD")
        private val accessLogStatuses=Array("200","201","204","301","400","404","500")

        private lazy val pathWords=
            dictionary.filter(w => w.nonEmpty && w.forall(c => c.isLetterOrDigit || c=='_' || c=='-'))

        private def dottedIpv4(i:Int)=
            s"${(i*13)&0xff}.${(i*7+1)&0xff}.${(i*3+5)&0xff}.${(i*11+9)&0xff}"

        private def accessLogLine(i:Int, ip:String):String={
            val ts=f"2026-${(i%12)+1}%02d-${(i%28)+1}%02dT${i%24}%02d:${i%60}%02d:${(i*7)%60}%02d.${(i*13)%1000}%03dZ"
            val level=accessLogLevels(i % accessLogLevels.length)
            val service=accessLogServices(i % accessLogServices.length)
            val method=accessLogMethods(i % accessLogMethods.length)
            val status=accessLogStatuses(i % accessLogStatuses.length)
            val dur=s"${i%500}ms"
            val prefix=s"$ts $level $service $method "
            val suffix=s" $status $dur $ip"
            val pathBudget=math.max(2, accessLogTargetLen - prefix.length - suffix.length)
            val pw=pathWords
            val path=new StringBuilder(pathBudget+16)
            path+='/'
            var k=i
            while (path.length < pathBudget) {
                if (path.length > 1) path+='/'
                path++=pw(k % pw.length)
                k += 1
            }
            if (path.length > pathBudget) path.setLength(pathBudget)
            if (path.last=='/') path.setLength(path.length-1)
            if (path.length < 2) path++="x"
            prefix + path.toString + suffix
        }

        lazy val ACCESSLOG_SAMPLE_HIT= accessLogLine(42, "203.0.113.88")
        lazy val ACCESSLOG_SAMPLE_MISS= accessLogLine(42, "-")

        val ACCESSLOG_LINES_HIT= Util.Memoize{ (i:Int) =>
            repeatToLength(i, k => accessLogLine(k, dottedIpv4(k)))
        }

        val ACCESSLOG_LINES_MISS= Util.Memoize{ (i:Int) =>
            repeatToLength(i, k => accessLogLine(k, "-"))
        }

        val JUMBOpattern= Util.Memoize{ (i:Int) =>
            val n= 1<<(i);
            val wordCount= n /10
            val extraBit= (n % 10) match {
                case 1 => "x"
                case 2 => "hi"
                case 4 => "hola"
                case 6 => "shalom"                                        
                case 8 => "bon jour"                                        
            }
            (extraBit :: words.take(wordCount)).mkString("|")
        }

    }


}

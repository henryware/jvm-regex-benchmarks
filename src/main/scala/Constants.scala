
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

        val ABCpat= """[ -~]*ABCDEFGHIJKLMNZ$""";

        val LONG_TEXT_ABC= Util.Memoize{ (i:Int) =>
            s"""${ALMOST_LONG_TEXT(i)}ABCDEFGHIJKLMNZ""""
        }

        val PHONE_NUM="""(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})""";

        val PARSE_PHONE_NUM="""\(?(\d{3})\)?\s?-\s{0,2}(\d{3})-(\d{4})""";


        lazy val words={
            scala.io.Source.fromResource("words.txt").
                getLines.
                filter(_.size==9).
                toList |>
                scala.util.Random.shuffle
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

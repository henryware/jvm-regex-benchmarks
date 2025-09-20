import org.openjdk.jmh.annotations._
import scala.compiletime.uninitialized
import worldofregex._
import java.util.concurrent.TimeUnit

package RBench {


    /* ZBig contains the normal tests: throughput tests vs varying length texts
     *
     * These are not expected to be especially slow
     */
    @State(Scope.Benchmark)
    @Warmup(iterations = 2, time=3401, timeUnit=TimeUnit.MILLISECONDS)
    @Measurement(iterations = 4, time=7601, timeUnit=TimeUnit.MILLISECONDS)
    @Timeout(time = 10000, timeUnit = TimeUnit.MILLISECONDS)
    @BenchmarkMode(Array(Mode.Throughput))
    @Fork(1)
    class ZBig{
        import Constants._

        @Param(Array("KMY", "Joni", "Florian", "MonqJFA","BricsScreen", "DkBrics","JavaUtil", "Re2J", "JiTrex", "Needle", "HarpoDFA", "HarpoInterp"))
        var factoryName:String = uninitialized

        var regexes:List[Regex]= uninitialized

        var engine:RegexEngine = uninitialized

        var phoneNumber:Regex = uninitialized
        var abcEnd:Regex = uninitialized
        var star:Regex = uninitialized

        //@Param(Array("8","12","16"))
        @Param(Array("5","6","7","8","9","10","11","12","13","14","15","16", "17","18","19","20","21","22"))
        var index:Int = uninitialized 

        var aaa:Regex = uninitialized

        var ax:Regex = uninitialized

        @Setup
        def prepare():Unit={
            val engineClass=Class.forName("worldofregex."+factoryName+"$");
            engine=engineClass.getField("MODULE$").get(null).asInstanceOf[RegexEngine]

            phoneNumber=engine.compile(PHONE_NUM)
            star=engine.compile(".*")
            
            aaa=engine.compile(AAApat(index))
            ax=engine.compile(AXpat)
            abcEnd=engine.compile(ABCpat)


        }

        @Benchmark
        def Compile_Long_Pattern()= {
            engine.compile(JUMBOpattern(index))
        }


        @Benchmark
        def DotStar_vs_Long_Text()= {
            if (star.hasWholeMatch(LONG_TEXT_PN(index))){
                    1
            } else {
                0
            }
        }


        @Benchmark
        def Match_Phone_Number_in_Long_Text()= {
            if (phoneNumber.hasPartialMatch(LONG_TEXT_PN(index))){
                    1
            } else {
                0
            }
        }

        @Benchmark
        def Fail_to_Match_Phone_Number_in_Long_Text()= {
            if (phoneNumber.hasPartialMatch(LONG_TEXT(index))){
                    1
            } else {
                0
            }
        }

        @Benchmark
        def Locate_Phone_Number_in_Long_Text()= {
            if (phoneNumber.locateFirstMatchIn(LONG_TEXT_PN(index)) == None){
                    1
            } else {
                0
            }
        }

        @Benchmark
        def Fail_to_Locate_Phone_Number_in_Long_Text()= {
            if (phoneNumber.locateFirstMatchIn(LONG_TEXT(index)) == None){
                    1
            } else {
                0
            }
        }

        @Benchmark
        def Match_ABC_in_Long_Text()= {
            if (abcEnd.hasPartialMatch(LONG_TEXT_ABC(index))){
                1
            } else {
                0
            }
        }

    }

    /* ZSmall contains throughput tests vs varying length texts
     *
     * These are potentially VERY VERY SLOW
     */
    @State(Scope.Benchmark)
    @Warmup(iterations = 2, time=3401, timeUnit=TimeUnit.MILLISECONDS)
    @Measurement(iterations = 4, time=7601, timeUnit=TimeUnit.MILLISECONDS)
    @Timeout(time = 10000, timeUnit = TimeUnit.MILLISECONDS)
    @BenchmarkMode(Array(Mode.Throughput))
    @Fork(1)
    class ZSmall {
        import Constants._

        @Param(Array("KMY", "Joni", "Florian", "MonqJFA", "BricsScreen", "DkBrics",  "JavaUtil", "Re2J", "JiTrex", "Needle", "HarpoDFA", "HarpoInterp"))
        var factoryName:String = uninitialized

        // @Param(Array("6","8","10"))
        @Param(Array("5","6","7","8","9","10","11","12","13","14"))
        var index:Int = uninitialized 

        var engine:RegexEngine = uninitialized

        var aaa:Regex = uninitialized

        var ax:Regex = uninitialized


        @Setup
        def prepare():Unit={
            val engineClass=Class.forName("worldofregex."+factoryName+"$");
            engine=engineClass.getField("MODULE$").get(null).asInstanceOf[RegexEngine]

            aaa=engine.compile(AAApat(index))
            ax=engine.compile(AXpat)
        }

        @Benchmark
        def Locate_All_Torture_Test()= {
            if (index>17) {
                -1  // too slow to run
            } else {
                var sum=0;
                for (r <- ax.locateAllMatchIn(AB(index))) do {
                    sum += r.start;
                }
                sum
            }
        }
    }

    /* TSmall contains timed tests
     *
     * These are potentially VERY VERY VERY SLOW as index gets large
     */
    @State(Scope.Benchmark)
    @Warmup(iterations = 2, time=3401, timeUnit=TimeUnit.MILLISECONDS)
    @Measurement(iterations = 4, time=7601, timeUnit=TimeUnit.MILLISECONDS)
    @Timeout(time = 10000, timeUnit = TimeUnit.MILLISECONDS)
    @BenchmarkMode(Array(Mode.AverageTime))
    @Fork(1)
    class TSmall {
        import Constants._

        @Param(Array("KMY", "Joni", "Florian", "MonqJFA","BricsScreen", "DkBrics","JavaUtil", "Re2J", "JiTrex"))
        var factoryName:String = uninitialized

        //@Param(Array("4","8","12"))
        @Param(Array("2","3","4","5","6","7","9","11","13","15","17"))
        var index:Int = uninitialized 

        var engine:RegexEngine = uninitialized

        var aaa:Regex = uninitialized

        @Setup
        def prepare():Unit={
            val engineClass=Class.forName("worldofregex."+factoryName+"$");
            engine=engineClass.getField("MODULE$").get(null).asInstanceOf[RegexEngine]

            aaa=engine.compile(AAApat(index))
        }

        @Benchmark
        def Backtrack_Torture_Test()= {
            if (index>=20) {
                -1  // too slow to run
            } else if (aaa.locateFirstMatchIn(AAA(index)) == None){
                1
            } else {
                0
            }
        }

    }



}

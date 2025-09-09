/* Read in benchmarks from CSV and create HTML files with plots */

package worldofregex {


    object Graph {

        import com.github.tototoshi.csv._

        import plotly._
        import plotly.element._
        import plotly.layout._

        def loadBenchmarks(filename:String) = {
            val rawRows=scala.util.Using(CSVReader.open(new java.io.File(filename)))(_.allWithHeaders()).get
            rawRows
        }


        def plot(skipThese:String*)={

            val filename="jmh-result.csv"

            val skipSet=skipThese.toSet

            import plotly.*
            import plotly.element.*
            import plotly.layout.*

            (new java.io.File("plots")).mkdir()

            val benchmarks=loadBenchmarks(filename)
            val zName2Rows=
                benchmarks.filter(r => r("Mode")=="thrpt"  && r("Score").toDouble>0 && !skipSet.contains(r("Param: factoryName"))).
                groupBy(_("Benchmark")).toList.sortBy(_._1) 
            val tName2Rows:List[(String,List[Map[String,String]])]= 
                benchmarks.filter(r => r("Mode")=="avgt"  && r("Score").toDouble>0 && !skipSet.contains(r("Param: factoryName"))).
                groupBy(_("Benchmark")).toList.sortBy(_._1) 


            def lastSegment(dottedString:String)={
                val a=dottedString.split("[.]")
                a(a.size -1)
            }

            def benchname2Filename(s:String)=s"${lastSegment(s)}.html"

            val allBenchmarkNames=zName2Rows.map(_._1) ++ tName2Rows.map(_._1);
            val fileNames=allBenchmarkNames.map(n=> benchname2Filename(n))

            val prefetches= fileNames.map{fn=> s"""<link rel="prefetch" href="$fn"/>"""}.mkString

            val links= allBenchmarkNames.
                map{s=> s"""<li><a href="${benchname2Filename(s)}">${lastSegment(s).replaceAll("_"," ")}</a></li>"""}
            val linksList=links.mkString("<ul>","","</ul>");
            val linksListWithMenu=links.mkString("""<ul><li><a href="index.html">Menu</a></li>""","","</ul>");


            def writeToFile(filename: String, content: String): Unit = {
                scala.util.Using(new java.io.PrintWriter(filename))(_.write(content)).get
            }

            def title(name:String)= {
                def formTitle(h1:String,h2:String="",h3:String="") = {
                    val q2=h2.replaceAll("""̈\\""","""&Backslash;""") 
                    s"""<b>${h1.replaceAll("_"," ")}</b><br>${q2}<br><sup>${h3}</sup>"""
                }

                name match {
                    case "DotStar_vs_Long_Text" => formTitle(name, "/.*/ vs ⟨random ascii printable string⟩", "higher is better")
                    case "Match_Phone_Number_in_Long_Text" => formTitle(name, """/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/ vs ⟨random ascii printable string⟩⟨phoneNumber⟩""","higher is better")
                    case "Fail_to_Match_Phone_Number_in_Long_Text" => formTitle(name, """/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/ vs ⟨ascii printable string⟩""","higher is better")
                    case "Locate_Phone_Number_in_Long_Text" => formTitle(name, """/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/ ⟨random ascii printable string⟩⟨phoneNumber⟩""","higher is better")
                    case "Fail_to_Locate_Phone_Number_in_Long_Text" => formTitle(name, """/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/ vs ⟨ascii printable string⟩""","higher is better")
                    case "Compile_Long_Pattern" => formTitle(name, """/word1|word2|word3.../""","higher is better")
                    case "Locate_All_Torture_Test" => formTitle(name, "repeatedly: /a(.*X)?/ vs a+", "higher is better")
                    case "Backtrack_Torture_Test" => formTitle(name, "/(a?)ᴺaᴺ/ vs aᴺ" , "lower is better (DFAs too fast to measure)")
                    case _ => formTitle(name);
                }
            }

            def html(name:String,engine2XYs:Iterable[(String,List[(Int,Double)])],xaxis:String, yaxis:String)={
                import Plotly.*;

                val traces={
                    for (case (engine,xys) <- engine2XYs) yield {
                        val (xs, ys)=xys.unzip
                        plotly.Scatter(xs,ys,name=engine)
                    }
                }.toSeq

                val layout = Layout(
                    title = title(lastSegment(name)),
                    height = 800,
                    xaxis = Axis(title = xaxis, `type` = AxisType.Log),
                    yaxis = Axis(title = yaxis, `type` = AxisType.Log)
                )

                val divId = "chart";

                s"""<!DOCTYPE html>
                |<html>
                |<head>
                |<meta charset="UTF-8">
                |<meta name="viewport" content="width=device-width, initial-scale=1.0">
                |<meta name="view-transition" content="same-origin"> <!-- Opt-in --> 
                |<link rel="prefetch"href="index.html"/>
                |${prefetches}
                |<title>${layout.title.getOrElse("plotly chart")}</title>
                | <style>
                |.chart {
	          |  view-transition-name: chart-div; /* Unique identifier for transition */
                |}
                |@keyframes fade-in {
                | from { opacity: 0; }
                | to { opacity: 1; }
                |}
                |a {
                | display: block;
                | text-align: left;
                | margin-top: 20px;
                | color: #0066cc;
                | text-decoration: none;
                | }
                |::view-transition-new(root) {
                | animation: fade-in 0.9s ease;
                | }
                |</style>
                |<script src="https://cdn.plot.ly/plotly-${if false then plotlyVersion else "1.58.5"}.min.js"></script>
                |</head>
                |<body>
                |<div id="$divId"></div>
                |<script>
                |${jsSnippet(divId, traces, layout, Config())}
                |</script>
                |${linksListWithMenu}
                |</body>
                |</html>
                """.stripMargin
            }

            /* Throughput Tests */

            for (case (bench,rows) <- zName2Rows) do {

                // rows is all the rows for a benchmark
                val engine2IndexScores=rows.groupMap(_("Param: factoryName")){
                    h=> (h("Param: index").toInt,h("Score").toDouble)
                }.toList.sortBy(_._1)
                val engine2XYs=for ((engine,indexScores) <- engine2IndexScores) yield {
                    (engine,
                     indexScores.map{case (i,s) => val len=1<<i; (len, len.toDouble*s)}
                    ) 
                }
                writeToFile("plots/"+benchname2Filename(bench),
                            html(bench,engine2XYs,xaxis="Data Length (bytes)", yaxis="Throughput(bytes/s)"));
            }

            /* Speed Tests */
            for (case (bench,rows) <- tName2Rows) do {

                // rows is all the rows for a benchmark
                val engine2XYs=rows.groupMap(_("Param: factoryName")){
                    h=> (h("Param: index").toInt,h("Score").toDouble)
                }.toList.sortBy(_._1)
                writeToFile("plots/"+benchname2Filename(bench),
                            html(bench,engine2XYs,xaxis="Data Length (bytes)", yaxis="Time(s/op)"));
            }



            writeToFile("plots/index.html",
                    s"""<!DOCTYPE html>
            |<html>
            |<head>
            |<meta charset="UTF-8">
            |<meta name="viewport" content="width=device-width, initial-scale=1.0">
            |<meta name="view-transition" content="same-origin"> <!-- Opt-in --> 
            |${prefetches}
            |<title>Benchmarks</title>
            | <style>
            |.chart {
	      |  view-transition-name: chart-div; /* Unique identifier for transition */
            |}
            |@keyframes fade-in {
            | from { opacity: 0; }
            | to { opacity: 1; }
            |}
            |a {
            | display: block;
            | text-align: left;
            | margin-top: 20px;
            | color: #0066cc;
            | text-decoration: none;
            | }
            |::view-transition-new(root) {
            | animation: fade-in 0.9s ease;
            | }
            |</style>
            |</head>
            |<body>
            |<p>See <a href="https://github.com/henryware/jvm-regex-benchmarks">https://github.com/henryware/jvm-regex-benchmarks</a></p>
            |${linksList}
            |</body>
            |</html>
       """.stripMargin)

        }

    }
}


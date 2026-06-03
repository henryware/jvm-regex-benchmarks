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

            val perEngineVariants = List(
                "Match_Phone_Ascii_Hit"   -> "Match A hit",
                "Match_Phone_Ascii_Miss"  -> "Match A miss",
                "Locate_Phone_Ascii_Hit"  -> "Loc A hit",
                "Locate_Phone_Ascii_Miss" -> "Loc A miss",
                "Match_Phone_Cjk_Hit"     -> "Match U hit",
                "Match_Phone_Cjk_Miss"    -> "Match U miss"
            )
            val perEngineBenchSet = perEngineVariants.map(_._1).toSet

            import plotly.*
            import plotly.element.*
            import plotly.layout.*

            val plotsDir = new java.io.File("plots")
            plotsDir.mkdir()
            Option(plotsDir.listFiles((_, name) => name.startsWith("Engine_") && name.endsWith(".html")))
                .toList.flatten.foreach(_.delete())

            val benchmarks=loadBenchmarks(filename)
            val runDate = {
                val mtime = new java.io.File(filename).lastModified()
                java.time.Instant.ofEpochMilli(mtime)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }
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

            val perEngineEngines = benchmarks
                .filter(r => r("Mode") == "thrpt"
                          && perEngineBenchSet.contains(lastSegment(r("Benchmark")))
                          && !skipSet.contains(r("Param: factoryName")))
                .map(_("Param: factoryName")).distinct.sorted
            val perEngineFileNames = perEngineEngines.map(e => s"Engine_$e.html")

            // Reflectively load each engine and ask for its version; missing
            // FFI native libs (or any other failure) fall back to blank.
            def loadVersion(engineName: String): String =
                try {
                    val cls = Class.forName("worldofregex." + engineName + "$")
                    cls.getField("MODULE$").get(null).asInstanceOf[RegexEngine].version
                } catch { case _: Throwable => "" }
            val engineVersions: Map[String, String] =
                perEngineEngines.map(e => e -> loadVersion(e)).toMap
            def versionSpan(engine: String): String =
                engineVersions.getOrElse(engine, "") match {
                    case "" => ""
                    case v  =>
                        val prefix = if v.headOption.exists(_.isDigit) then "v" else ""
                        s""" <span style="font-size:smaller">$prefix$v</span>"""
                }

            val prefetches= (fileNames ++ perEngineFileNames).map{fn=> s"""<link rel="prefetch" href="$fn"/>"""}.mkString

            val benchLinks= allBenchmarkNames.
                map{s=> s"""<li><a href="${benchname2Filename(s)}">${lastSegment(s).replaceAll("_"," ")}</a></li>"""}
            val engineLinks = perEngineEngines.
                map{e => s"""<li><a href="Engine_$e.html">$e</a></li>"""}
            val linksListWithMenu=
                benchLinks.mkString("""<ul><li><a href="index.html">Menu</a></li>""","","</ul>") +
                (if engineLinks.nonEmpty
                 then engineLinks.mkString("<h2>Per-Engine Comparisons</h2><ul>","","</ul>")
                 else "")


            def writeToFile(filename: String, content: String): Unit = {
                scala.util.Using(new java.io.PrintWriter(filename))(_.write(content)).get
            }

            val benchDescriptions: Map[String, (String, String)] = Map(
                "WholeMatch_DotStar_Ascii" -> ("/.*/ vs ⟨random ascii printable string⟩", "higher is better"),
                "Match_Phone_Ascii_Hit"    -> ("""/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/ vs ⟨random ascii printable string⟩⟨phoneNumber⟩""", "higher is better"),
                "Match_Phone_Ascii_Miss"   -> ("""/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/ vs ⟨ascii printable string⟩""", "higher is better"),
                "Match_Phone_Cjk_Hit"      -> ("""/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/ vs ⟨random CJK Unified Ideographs⟩⟨phoneNumber⟩""", "higher is better"),
                "Match_Phone_Cjk_Miss"     -> ("""/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/ vs ⟨random CJK Unified Ideographs⟩""", "higher is better"),
                "Locate_Phone_Ascii_Hit"   -> ("""/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/ ⟨random ascii printable string⟩⟨phoneNumber⟩""", "higher is better"),
                "Locate_Phone_Ascii_Miss"  -> ("""/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/ vs ⟨ascii printable string⟩""", "higher is better"),
                "Locate_Company_Cjk_Hit"   -> ("""/[一-鿿]{2,8}(?:株式会社|有限公司|股份有限公司)(?:第[0-9一-鿿]{1,3}号)?/ vs ⟨random CJK Unified Ideographs⟩⟨company name⟩""", "higher is better"),
                "Locate_Company_Cjk_Miss"  -> ("""/[一-鿿]{2,8}(?:株式会社|有限公司|股份有限公司)(?:第[0-9一-鿿]{1,3}号)?/ vs ⟨random CJK Unified Ideographs⟩""", "higher is better"),
                "Compile_Jumbo"            -> ("""/word1|word2|word3.../""", "higher is better"),
                "LocateAll_Torture_Test"   -> ("repeatedly: /a(.*X)?/ vs a+", "higher is better"),
                "Match_Abc_Ascii_Hit"      -> ("/[ -~]*ABCDEFGHIJKLMNZ/ vs <ascii printable> \"ABCDEFGHIJKLMNZ\"", "higher is better"),
                "Backtrack_Torture_Test"   -> ("/(a?)ᴺaᴺ/ vs aᴺ", "lower is better (DFAs and JITrex are too fast to measure)")
            )

            def description(name:String):String =
                benchDescriptions.get(name).map(_._1.replaceAll("""̈\\""","""&Backslash;""")).getOrElse("")

            def title(name:String):String = {
                val (pattern, note) = benchDescriptions.getOrElse(name, ("",""))
                val q2 = pattern.replaceAll("""̈\\""","""&Backslash;""")
                s"""<b>${name.replaceAll("_"," ")}</b><br>${q2}<br><span style="font-size:smaller">${note}</span>"""
            }

            def html(name:String,engine2XYs:Iterable[(String,List[(Int,Double)])],xaxis:String, yaxis:String, customTitle:Option[String]=None)={
                import Plotly.*;

                val traces={
                    for (case (engine,xys) <- engine2XYs) yield {
                        val (xs, ys)=xys.unzip
                        plotly.Scatter(xs,ys,name=engine)
                    }
                }.toSeq

                val layout = Layout(
                    title = customTitle.getOrElse(title(lastSegment(name))),
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
                |<title>${lastSegment(name).replaceAll("_"," ")}</title>
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
                |<p>Benchmark code and descriptions are at <a href="https://github.com/henryware/jvm-regex-benchmarks">https://github.com/henryware/jvm-regex-benchmarks</a></p>
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
                val xaxis = lastSegment(bench) match {
                    case "Compile_Jumbo" => "Pattern length (chars)"
                    case _               => "Data Length (chars)"
                }
                writeToFile("plots/"+benchname2Filename(bench),
                            html(bench,engine2XYs,xaxis=xaxis, yaxis="Throughput(chars/s)"));
            }

            /* Per-Engine Comparison Plots */
            for (engine <- perEngineEngines) do {
                val variantTraces: List[(String, List[(Int, Double)])] =
                    perEngineVariants.map { case (benchSuffix, label) =>
                        val xys = benchmarks.iterator
                            .filter(r => r("Mode") == "thrpt"
                                      && r("Score").toDouble > 0
                                      && r("Param: factoryName") == engine
                                      && lastSegment(r("Benchmark")) == benchSuffix)
                            .map(r => (r("Param: index").toInt, r("Score").toDouble))
                            .toList.sortBy(_._1)
                            .map { case (i, s) => val len = 1 << i; (len, len.toDouble * s) }
                        (label, xys)
                    }.filter(_._2.nonEmpty)
                val customTitle = s"""<b>$engine</b>${versionSpan(engine)}<br>Phone-number throughput: {success|fail} × {match|locate}<br><span style="font-size:smaller">higher is better</span>"""
                writeToFile(s"plots/Engine_$engine.html",
                    html(s"Engine_$engine", variantTraces,
                         xaxis = "Data Length (chars)",
                         yaxis = "Throughput(chars/s)",
                         customTitle = Some(customTitle)))
            }

            /* Speed Tests */
            for (case (bench,rows) <- tName2Rows) do {

                // rows is all the rows for a benchmark
                val engine2XYs=rows.groupMap(_("Param: factoryName")){
                    h=> (h("Param: index").toInt,h("Score").toDouble)
                }.toList.sortBy(_._1)
                val xaxis = lastSegment(bench) match {
                    case "Backtrack_Torture_Test" => "N (pattern depth = string length)"
                    case _                      => "Data Length (chars)"
                }
                writeToFile("plots/"+benchname2Filename(bench),
                            html(bench,engine2XYs,xaxis=xaxis, yaxis="Time(s/op)"));
            }



            def fmtThroughput(cps:Double):String =
                if cps >= 1e9 then f"${cps/1e9}%.2f Gchars/s"
                else if cps >= 1e6 then f"${cps/1e6}%.0f Mchars/s"
                else if cps >= 1e3 then f"${cps/1e3}%.0f Kchars/s"
                else f"$cps%.0f chars/s"

            def heatColor(value:Double, lo:Double, hi:Double):String =
                if value <= 0 || hi <= lo then "#ffffff"
                else {
                    val t = (math.log(value) - math.log(lo)) / (math.log(hi) - math.log(lo))
                    val lightness = (92 - 50 * t).toInt
                    s"hsl(120, 55%, $lightness%)"
                }

            // For each throughput benchmark: (shortName, maxIdxBytes, sortedTopEngines)
            val winnersRows = zName2Rows.map { case (bench, rows) =>
                val short = lastSegment(bench)
                val maxIdx = rows.map(_("Param: index").toInt).max
                val len = 1 << maxIdx
                val ranked = rows.filter(_("Param: index").toInt == maxIdx)
                    .map(r => (r("Param: factoryName"), r("Score").toDouble * len))
                    .sortBy(-_._2)
                (short, len, ranked)
            }

            val winnersHtml = {
                val body = winnersRows.map { case (short, len, ranked) =>
                    val cells = (0 until 3).map { i =>
                        if i < ranked.length then {
                            val (eng, bps) = ranked(i)
                            s"""<td><a href="Engine_${eng}.html">${eng}</a> <small>(${fmtThroughput(bps)})</small></td>"""
                        } else "<td></td>"
                    }.mkString
                    val sizeLabel = if len >= (1<<20) then s"${len>>20} Mchars" else if len >= (1<<10) then s"${len>>10} Kchars" else s"$len chars"
                    s"""<tr><td><a href="${short}.html">${short.replaceAll("_"," ")}</a><br><small>at $sizeLabel</small></td>$cells</tr>"""
                }.mkString
                s"""<h2>Throughput Winners</h2>
                |<p class="hint">Top three engines on each throughput benchmark, evaluated at the largest input size measured.</p>
                |<table class="winners">
                |<thead><tr><th>Benchmark</th><th>1st</th><th>2nd</th><th>3rd</th></tr></thead>
                |<tbody>$body</tbody>
                |</table>""".stripMargin
            }

            // Scorecard: (engine, variantBenchSuffix) -> chars/sec at the engine's largest available index
            val scorecard: Map[(String,String), Double] =
                benchmarks.filter(r =>
                    r("Mode") == "thrpt" &&
                    r("Score").toDouble > 0 &&
                    perEngineBenchSet.contains(lastSegment(r("Benchmark"))) &&
                    !skipSet.contains(r("Param: factoryName"))
                ).groupBy(r => (r("Param: factoryName"), lastSegment(r("Benchmark"))))
                 .map { case (k, rs) =>
                     val maxIdx = rs.map(_("Param: index").toInt).max
                     val bps = rs.filter(_("Param: index").toInt == maxIdx)
                         .map(r => r("Score").toDouble * (1 << maxIdx)).max
                     k -> bps
                 }

            val colStats: Map[String, (Double, Double)] =
                perEngineVariants.map { case (suffix, _) =>
                    val vals = perEngineEngines.flatMap(e => scorecard.get((e, suffix)))
                    suffix -> (if vals.isEmpty then (0.0, 0.0) else (vals.min, vals.max))
                }.toMap

            val scorecardHtml = {
                val headerCells = perEngineVariants.map { case (_, label) => s"<th>${label}</th>" }.mkString
                val rows = perEngineEngines.map { engine =>
                    val cells = perEngineVariants.map { case (suffix, _) =>
                        scorecard.get((engine, suffix)) match {
                            case Some(bps) =>
                                val (lo, hi) = colStats(suffix)
                                s"""<td style="background-color:${heatColor(bps, lo, hi)}">${fmtThroughput(bps)}</td>"""
                            case None => """<td class="na">—</td>"""
                        }
                    }.mkString
                    s"""<tr><th class="rowhead"><a href="Engine_${engine}.html">${engine}</a></th>$cells</tr>"""
                }.mkString
                s"""<h2>Phone-Number Scorecard</h2>
                |<p class="hint">Throughput at each engine's largest measured input. Cells are color-coded by column (log scale: darker = faster). Click an engine to see its full {match|locate} × {success|fail} chart.</p>
                |<table class="scorecard">
                |<thead><tr><th>Engine</th>$headerCells</tr></thead>
                |<tbody>$rows</tbody>
                |</table>""".stripMargin
            }

            val benchListHtml = {
                val items = allBenchmarkNames.map { name =>
                    val short = lastSegment(name)
                    val desc = description(short)
                    val descLine = if desc.nonEmpty then s"""<div class="bench-desc">${desc}</div>""" else ""
                    s"""<li><a href="${benchname2Filename(name)}">${short.replaceAll("_"," ")}</a>$descLine</li>"""
                }.mkString
                s"""<h2>All Benchmarks</h2><ul class="bench-list">$items</ul>"""
            }

            val engineListHtml = {
                val items = perEngineEngines.map(e =>
                    s"""<li><a href="Engine_${e}.html">${e}</a>${versionSpan(e)}</li>"""
                ).mkString
                s"""<h2>Per-Engine Comparisons</h2><ul class="engine-list">$items</ul>"""
            }

            writeToFile("plots/index.html",
                    s"""<!DOCTYPE html>
            |<html>
            |<head>
            |<meta charset="UTF-8">
            |<meta name="viewport" content="width=device-width, initial-scale=1.0">
            |<meta name="view-transition" content="same-origin"> <!-- Opt-in -->
            |${prefetches}
            |<title>JVM Regex Benchmarks</title>
            | <style>
            |body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; max-width: 1100px; margin: 1em auto; padding: 0 1em; color: #222; }
            |h1 { margin-bottom: 0.2em; }
            |h2 { margin-top: 2em; border-bottom: 1px solid #ddd; padding-bottom: 0.3em; }
            |a { color: #0066cc; text-decoration: none; }
            |a:hover { text-decoration: underline; }
            |p.hint { color: #555; font-size: 0.9em; margin-top: 0; }
            |table { border-collapse: collapse; margin: 1em 0; font-size: 0.92em; }
            |th, td { padding: 4px 10px; text-align: left; border: 1px solid #ddd; }
            |th { background: #f4f4f4; font-weight: 600; }
            |table.winners td small { color: #777; }
            |table.scorecard td { font-family: ui-monospace, "SFMono-Regular", Menlo, monospace; text-align: right; }
            |table.scorecard th.rowhead { text-align: left; background: #f9f9f9; }
            |table.scorecard td.na { color: #aaa; text-align: center; background: #fafafa; }
            |ul.bench-list { list-style: none; padding-left: 0; }
            |ul.bench-list li { margin-bottom: 0.9em; }
            |ul.bench-list .bench-desc { font-size: 0.85em; color: #666; margin-left: 1.2em; font-family: ui-monospace, "SFMono-Regular", Menlo, monospace; }
            |ul.engine-list { list-style: none; padding-left: 0; columns: 4; column-gap: 1.5em; }
            |ul.engine-list li { break-inside: avoid; margin-bottom: 0.3em; }
            |::view-transition-new(root) { animation: fade-in 0.9s ease; }
            |@keyframes fade-in { from { opacity: 0; } to { opacity: 1; } }
            |</style>
            |</head>
            |<body>
            |<h1>JVM Regex Benchmarks</h1>
            |<p>Throughput and latency comparison across JVM-accessible regex engines. Benchmark code at <a href="https://github.com/henryware/jvm-regex-benchmarks">github.com/henryware/jvm-regex-benchmarks</a>.</p>
            |<p class="hint">Results from benchmark run on <b>${runDate}</b>.</p>
            |$winnersHtml
            |$scorecardHtml
            |$benchListHtml
            |$engineListHtml
            |</body>
            |</html>
       """.stripMargin)

        }

    }
}


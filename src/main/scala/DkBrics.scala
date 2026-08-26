package worldofregex;

import worldofregex.Util.manglePattern

object DkBrics extends RegexEngine {
    val name="DkBrics"
    val version=LibraryVersion.fromClass(classOf[dk.brics.automaton.RegExp])
    import dk.brics.automaton.RegExp // Import RegExp to access flags

    def compile(pattern:String):Regex ={

        val auto = new dk.brics.automaton.RegExp(manglePattern(pattern), RegExp.NONE).toAutomaton;
        val runAuto= new dk.brics.automaton.RunAutomaton(auto, true)
        new Regex {
            override def toString= s"DkBrics($pattern)"

            val engineName="DkBrics"

            // RunAutomaton is immutable/stateless, so the matcher just holds a
            // reference to it; per-call state lives in the newMatcher() cursor.
            def matcher():Matcher= new Matcher {

                def hasWholeMatch(txt:String):Boolean=runAuto.run(txt)

                def hasPartialMatch(txt:String):Boolean=runAuto.newMatcher(txt).find();

                def locateFirstMatchIn(txt:String):Option[Location]={
                    val am = runAuto.newMatcher(txt)
                    am.find() match {
                        case true => Some(Location(am.start, am.end))
                        case false => None
                    }
                }

                def locateAllMatchIn(txt:String):Iterator[Location]={
                    Iterator.unfold(runAuto.newMatcher(txt)){(ame) =>
                        ame.find match {
                            case true => Some((Location(ame.start,ame.end)),ame);
                            case false => None
                        }
                    }
                }
            }

        }
    }
}

package worldofregex

import RegexAST.*

object RegexParser {

    def parse(input: String):  RegexNode = {
        val parser = new RegexParser(input)
        parser.parsePattern() match {
            case Right(node) if parser.getPos == input.length => node
            case Right(_) => throw new java.util.regex.PatternSyntaxException(s"Unexpected characters after pattern at position ${parser.getPos}",input,parser.getPos);
            case Left(error) => throw new java.util.regex.PatternSyntaxException(error,input,parser.getPos);
        }
    }
    def parseMonadic(input: String):  Either[String, RegexNode] = {
        val parser = new RegexParser(input)
        parser.parsePattern() match {
            case Right(node) if parser.getPos == input.length => Right(node)
            case Right(_) => Left(s"Unexpected characters after pattern at position ${parser.getPos}")
            case Left(error) => Left(error)
        }
    }
}

class RegexParser(input: String) {
    private var pos: Int = 0
    def getPos=pos

    private def peek: Option[Char] = if (pos < input.length) Some(input(pos)) else None
    private def consume(): Option[Char] = {
        if (pos < input.length) {
            val c = input(pos)
            pos += 1
            Some(c)
        } else None
    }
    private def consume(expected: Char): Boolean = {
        if (peek.contains(expected)) {
            pos += 1
            true
        } else false
    }
    private def consume(expected: String): Boolean = {
        if (input.startsWith(expected, pos)) {
            pos += expected.length
            true
        } else false
    }

    private def parseNumber(): Either[String, Int] = {
        var numStr = ""
        while (peek.exists(_.isDigit)) {
            numStr += consume().get
        }
        if (numStr.isEmpty) Left(s"Expected number at position $pos")
        else Right(numStr.toInt)
    }

    def parsePattern(): Either[String, RegexNode] = if (input.length==0){
        Right(Empty)
    } else {
        parseAlternation()
    }

    private def parseAlternation(): Either[String, RegexNode] = {
        var nodes: List[RegexNode] = Nil
        parseConcatenation() match {
            case Right(head) => nodes = nodes :+ head
            case Left(err) => return Left(err)
        }

        while (consume('|')) {
            parseConcatenation() match {
                case Right(next) => nodes = nodes :+ next
                case Left(err) => return Left(err)
            }
        }
        
        nodes match {
            case single :: Nil => Right(single)
            case multiple => Right(Alternation(multiple))
        }
    }

    private def parseConcatenation(): Either[String, RegexNode] = {
        var nodes: List[RegexNode] = Nil
        while (peek.exists(c => !Set('|', ')').contains(c) || c == '(')) {
            parseTerm() match {
                case Right(node) => nodes = nodes :+ node
                case Left(err) => return Left(err)
            }
        }
        nodes match {
            case Nil => Left(s"Expected term at position $pos")
            case list => Right(Concatenation(list))
        }
    }

    private def parseTerm(): Either[String, RegexNode] = {
        parseFactor() match {
            case Right(node) =>
                parseQuantifier() match {
                    case Some(quant) => Right(quant(node))
                    case None => Right(node)
                }
            case Left(err) => Left(err)
        }
    }

    private def parseQuantifier(): Option[RegexNode => Quantifier] = {
        peek match {
            case Some('*') =>
                consume();
                val isGreedy = !consume('?')
                Some(node => Quantifier(node, 0, None, isGreedy))
            case Some('+') =>
                consume()
                val isGreedy = !consume('?')
                Some(node => Quantifier(node, 1, None, isGreedy))
            case Some('?') =>
                consume()
                val isGreedy = !consume('?')
                Some(node => Quantifier(node, 0, Some(1), isGreedy))
            case Some('{') =>
                consume()
                parseNumber() match {
                    case Right(min) =>
                        val maxOpt = if (consume(',')) {
                            parseNumber() match {
                                // {2,3}
                                case Right(max) => Some(max)
                                // {2,}
                                case Left(_) => None
                            }
                        } else {
                            // {2}
                            Some(min)
                        }
                        if (consume('}')) {
                            val isGreedy = !consume('?')
                            Some(node => Quantifier(node, min, maxOpt, isGreedy))
                        } else {
                            pos -= 1 // Backtrack for error message
                            None
                        }
                    case Left(_) => None
                }
            case _ => None
        }
    }

    private def parseFactor(): Either[String, RegexNode] = {
        peek match {
            case Some('(') => parseGroupOrLookaround()
            case Some('[') => parseCharClass()
            case Some('^') =>
                consume()
                Right(Anchor(true))
            case Some('$') =>
                consume()
                Right(Anchor(false))
            case Some('\\') => parseEscaped()
            case Some(c) if !Set('|', '.',')', '*', '+', '?', '{', '}').contains(c) =>
                consume()
                Right(Literal(c))
            case Some('.') =>
                consume()
                Right(AnyChar)
            case Some(c) =>
                Left(s"Unexpected character '$c' at position $pos")
            case None =>
                Left(s"Unexpected end of input at position $pos")
        }
    }

    private def parseGroupOrLookaround(): Either[String, RegexNode] = {
        consume('(') // Already checked for '('
        if (consume("?:")) {
            parsePattern() match {
                case Right(body) =>
                    if (consume(')')) {
                        Right(Group(body, false))
                    } else {
                        Left(s"Expected ')' for group at position $pos")
                    }
                case Left(err) =>
                    if (consume(')')) {
                        Right(Group(Empty,false))
                    } else {
                        Left(err)
                    }
            }
        } else if (consume('?')) {
            val isNegative = consume('!')
            val lookType = if (consume("<=")) "behind"
                           else if (consume("=")) "ahead"
                           else ""
            if (lookType.isEmpty) {
                Left(s"Invalid lookaround syntax at position $pos")
            } else {
                parsePattern() match {
                    case Right(body) =>
                        if (consume(')')) {
                            Right(LookAround(body, lookType == "ahead", !isNegative))
                        } else {
                            Left(s"Expected ')' for lookaround at position $pos")
                        }
                    case Left(err) => Left(err)
                }
            }
       } else {
            parsePattern() match {
                case Right(body) =>
                    if (consume(')')) {
                        Right(Group(body, true))
                    } else {
                        Left(s"Expected ')' for group at position $pos")
                    }
                case Left(err) =>
                    if (consume(')')) {
                        Right(Group(Empty,true));
                    } else {
                        Left(err)
                    }
            }
        }
    }

    private def parseCharClass(): Either[String, RegexNode] = {
        consume('[') // Already checked for '['
        val negated = consume('^')
        var ranges: List[CharRange] = Nil
        while (peek.exists(_ != ']')) {
            parseCharOrRange() match {
                case Right(range) => ranges = ranges :+ range
                case Left(err) => return Left(err)
            }
        }
        if (consume(']')) {
            Right(CustomCharClass(ranges, negated))
        } else {
            Left(s"Expected ']' for character class at position $pos")
        }
    }

    private def parseCharOrRange(): Either[String, CharRange] = {
        peek match {
            case Some(']') => Left(s"Unexpected end of character class at position $pos")
            case Some(c1) =>
                consume()
                if (peek.contains('-') && peek.get != input.last) {
                    consume('-')
                    peek match {
                        case Some(c2) => // if c2 != ']' =>
                            consume()
                            Right(CharRange(c1, c2))
                        case _ =>
                            Left(s"Invalid range in character class at position $pos")
                    }
                } else {
                    Right(CharRange(c1, c1))
                }
            case None =>
                Left(s"Unexpected end of input in character class at position $pos")
        }
    }

    private def parseEscaped(): Either[String, RegexNode] = {
        consume('\\') // Already checked for '\'
        peek match {
            case Some('d') => consume(); Right(PredefinedCharClass("d", false))
            case Some('w') => consume(); Right(PredefinedCharClass("w", false))
            case Some('s') => consume(); Right(PredefinedCharClass("s", false))
            case Some('D') => consume(); Right(PredefinedCharClass("d", true))
            case Some('W') => consume(); Right(PredefinedCharClass("w", true))
            case Some('S') => consume(); Right(PredefinedCharClass("s", true))
            case Some('t') => consume(); Right(Literal('\t'))
            case Some('n') => consume(); Right(Literal('\n'))
            case Some('r') => consume(); Right(Literal('\r'))
            case Some('f') => consume(); Right(Literal('\f'))
            case Some('b') => consume(); Right(Literal('\b'))
            case Some(c) =>
                consume()
                Right(Literal(c))
            case None =>
                Left(s"Incomplete escape sequence at position $pos")
        }
    }
}

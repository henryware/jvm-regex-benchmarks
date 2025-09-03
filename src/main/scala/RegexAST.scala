package worldofregex
import scala.math.Ordering

/** Base trait for all regex AST nodes */
sealed trait RegexNode

object RegexAST {
    val regexMetaChars = Set('\\', '.', '*', '+', '?', '^', '$', '(', ')', '[', ']', '{', '}', '|', '-')
    
    def parse(pattern: String): RegexNode = 
        RegexParser.parse(pattern)

    // fixme: this is naive.
    def escapeInClass(c: Char): String = c match {
        case '\\' => "\\\\"
        case ']' => "\\]" // jurp accepts unquoted in first spot, fixme
        case '-' => "-"  // jurp chokes if these are quoted.  Means literal in first spot (last spot?)
        case '^' => "^" // should be quoted in first spot, fixme 
        case _ if regexMetaChars.contains(c) => s"\\$c"
        case _ => c.toString
    }

    def toSpartanPattern(node: RegexNode): String = {
        import RegexCanonicalizer.*;
        toPattern(canonicalize(spartanize(node)))
    }


    def toPattern(node: RegexNode|CharRange): String = node match {
        case Empty => ""
        case Literal(char) => 
            char match {
                case '\t' => "\\t"
                case '\n' => "\\n"
                case '\r' => "\\r"
                case '\f' => "\\f"
                case '\b' => "\\b"
                case _ if regexMetaChars.contains(char) => s"\\$char"
                case _ => char.toString
            }
            
        case CustomCharClass(ranges, negated) =>
            val rangePatterns = ranges.map(toPattern)
            s"[${if (negated) "^" else ""}${rangePatterns.mkString}]"
            
        case PredefinedCharClass(name, negated) =>
            (negated,name(0).isUpper) match {
                case (true,true) => s"\\${name.toLowerCase}"
                case (true,false) => s"\\${name.toUpperCase}"
                case (false,_) => s"\\${name}"
            }
            
        case CharRange(start, end) =>
            if (start == end) escapeInClass(start)
            else s"${escapeInClass(start)}-${escapeInClass(end)}"
            
        case Quantifier(child, min, max, isGreedy) =>
            val base = toPattern(child)
            val quant = (min, max) match {
                case (0, None) => "*"
                case (1, None) => "+"
                case (0, Some(1)) => "?"
                case (m, None) => s"{$m,}"
                case (m, Some(n)) if m == n => s"{$m}"
                case (m, Some(n)) => s"{$m,$n}"
            }
            val greediness = if (isGreedy) "" else "?"
            val needsWrap = child.isInstanceOf[Alternation]
            val wrapped = if (needsWrap) s"($base)" else base
            s"$wrapped$quant$greediness"
            
        case Anchor(isStart) =>
            if (isStart) "^" else "$"
            
        case Group(body, capturing) =>
            val pat=toPattern(body)
            (capturing) match {
                case false => s"(?:$pat)"
                case true => s"($pat)"
            }

        case AnyChar => "."

        case Alternation(children) =>
            children.map(toPattern).mkString("|")
            
        case Concatenation(children) =>
            children.map {
                case alt: Alternation => s"(?:${toPattern(alt)})"
                case node => toPattern(node)
            }.mkString

        case LookAround(node, isLookAhead, isPositive) =>
            val lookType = if (isLookAhead) "=" else "<="
            val negation = if (isPositive) "" else "!"
            s"(?$negation$lookType${toPattern(node)})"
    }

    /** The empty regex */
    case object Empty extends RegexNode

    /** Literal character */
    case class Literal(char: Char) extends RegexNode
    
    /** Literal character */
    case object AnyChar extends RegexNode

    /** Character class for custom ranges like [a-z], [^a-z] */
    case class CustomCharClass(ranges: List[CharRange], negated: Boolean) extends RegexNode
    
    /** Predefined character class like \d, \w, \s and their negated versions */
    case class PredefinedCharClass(name: String, negated: Boolean) extends RegexNode
    
    /** Character range (e.g. a-z) */
    case class CharRange(start: Char, end: Char) derives CanEqual
    implicit val _charRangeOrdering: Ordering[CharRange] = Ordering.by(_.start)

    /** Quantifier (*, +, ?, {m,n}) */
    case class Quantifier(
        child: RegexNode, 
        min: Int,
        max: Option[Int],  // None means unbounded
        isGreedy: Boolean = true
    ) extends RegexNode
    
    /** Anchor (^ or $) */
    case class Anchor(isStart: Boolean)  extends RegexNode
    
    /** Group with optional capturing */
    case class Group(
        body: RegexNode, 
        capturing: Boolean = true
    ) extends RegexNode {
        val name=None;
    }

    /** Alternation (| operator) */
    case class Alternation(children: List[RegexNode]) extends RegexNode derives CanEqual
    
    object Alternation {
        def apply(nodes: RegexNode*): Alternation = Alternation(nodes.toList)
    }
    
    /** Concatenation of nodes */
    case class Concatenation(children: List[RegexNode]) extends RegexNode derives CanEqual {
        def :+(node: RegexNode): Concatenation = copy(children = children :+ node)
        def +:(node: RegexNode): Concatenation = copy(children = node +: children)
    }
    
    object Concatenation {
        def apply(nodes: RegexNode*): Concatenation = Concatenation(nodes.toList)
    }
    
    /** Lookahead/lookbehind assertion */
    case class LookAround(
        node: RegexNode,
        isLookAhead: Boolean,
        isPositive: Boolean
    ) extends RegexNode
    

    // for munit and scalacheck-derived
    given Ordering[RegexNode] = Ordering.by(_.toString)

}

package worldofregex

import worldofregex.RegexAST.*

object RegexCanonicalizer {

    /**
      * Converts a RegexAST into a canonical form.
      * This involves:
      * - Flattening nested Concatenation nodes.
      * - Unwrapping Concatenation nodes with a single child.
      * - Sorting and merging ranges in CustomCharClass nodes.
      * 
      *  It is intended to return a Node which is semantically equivalent to the
      *  original node.
      */
    def canonicalize(node: RegexNode): RegexNode = node match {
        case Concatenation(children) =>
            // Recursively canonicalize children and flatten any nested concatenations
            val newChildren = children.flatMap { child =>
                canonicalize(child) match {
                    case Concatenation(innerChildren) => innerChildren
                    case Empty => Nil
                    case otherNode => List(otherNode)
                }
            }
            // Unwrap if only one child, otherwise return a new Concatenation
            newChildren match {
                case Nil => Empty
                case single :: Nil => single
                case multiple => Concatenation(multiple)
            }

        case Alternation(children) =>
            val newChildren = children.flatMap { child =>
                canonicalize(child) match {
                    case Alternation(innerChildren) => innerChildren
                    case Empty => Nil
                    case otherNode => List(otherNode)
                }
            }
            newChildren match {
                case Nil => Empty
                case single :: Nil => Quantifier(single,0,Some(1),true) 
                case multiple => Alternation(multiple)
            }

        case Quantifier(child, min, max, isGreedy) => {
            val newChild=canonicalize(child)
            newChild match {
                case Empty => Empty;
                case _ => Quantifier(newChild, min, max, isGreedy);
            }
        }

        case Group(body, capturing) => {
            val newChild=canonicalize(body)
            (newChild,capturing) match {
                case (Empty,false) => Empty;
                case (a:Alternation,false) => a;
                case (g:Group,false) => g;
                case _ =>  Group(newChild, capturing)
            }
        }

        case LookAround(node, isLookAhead, isPositive) => {
            val newChild=canonicalize(node)
            newChild match {
                case Empty => Empty
                case _ =>  LookAround(newChild, isLookAhead, isPositive)
            }
        }

         
        case CustomCharClass(ranges, negated) =>
            if (ranges.isEmpty) {
                // should empty + negated be Literal('^')?  seems like this is the wrong level for that.   
                Empty
            } else {
                // Sort ranges to handle out-of-order definitions like [z-za-b]
                val sortedRanges = ranges.sorted
                // Fold to merge overlapping or adjacent ranges like [a-c] and [d-f] into [a-f]
                val mergedRanges = sortedRanges.tail.foldLeft(List(sortedRanges.head)) { (acc, range) =>
                    val last = acc.head
                    // Merge if the new range starts right after the previous one ends, or overlaps with it
                    if (last.end.toInt + 1 >= range.start.toInt) {
                        val newEnd = if (range.end > last.end) range.end else last.end
                        CharRange(last.start, newEnd) :: acc.tail
                    } else {
                        range :: acc
                    }
                }.reverse
                CustomCharClass(mergedRanges, negated)
            }

        case PredefinedCharClass(name, negated) =>
            if (Set("d", "w", "s").contains(name)) {
                PredefinedCharClass(name,negated)
            } else {
                Empty
            }
    

        // Leaf nodes are already in canonical form
        case other => other
    }


    /*  The NFA engines only accept a spartan subset of the regex ast
     * 
     *  This returns a Node which is semantically different.
     */
    def spartanize(node: RegexNode): RegexNode = node match {
        case Concatenation(children) => Concatenation(children.map(spartanize)) 

        case Alternation(children) => Alternation(children.map(spartanize))

        case Quantifier(child, min, max, isGreedy) =>
            Quantifier(spartanize(child),min,max,true)

        case la: LookAround =>
            Empty

        case an: Anchor =>
            Empty

        case Group(body, capturing) => {
            Group(spartanize(body),false);
        }

        case other => other
    }


}

package worldofregex

class SparseSet(val maxSize: Int) {
  require(maxSize > 0, "Max size must be positive")

  private val dense: Array[Int] = new Array[Int](maxSize)
  private val sparse: Array[Int] = new Array[Int](maxSize)
  private var currentSize: Int = 0

  def size: Int = currentSize

  def contains(x: Int): Boolean = {
    x >= 0 && x < maxSize && {
      val idx = sparse(x)
      idx < currentSize && dense(idx) == x
    }
  }

  def add(x: Int): Boolean = {
    if (x < 0 || x >= maxSize || contains(x)) {
        false
    } else {
        dense(currentSize) = x
        sparse(x) = currentSize
        currentSize += 1
        true
    }
  }

  def remove(x: Int): Boolean = {
    if (!contains(x)) false
    else {
      val index = sparse(x)
      currentSize -= 1
      val last = dense(currentSize)
      dense(index) = last
      sparse(last) = index
      true
    }
  }

  def clear(): Unit = {
    currentSize = 0
  }

  def iterator: Iterator[Int] = new Iterator[Int] {
    private var pos = 0
    override def hasNext: Boolean = pos < currentSize
    override def next(): Int = {
      if (hasNext) {
        val res = dense(pos)
        pos += 1
        res
      } else Iterator.empty.next()
    }
  }

  def toSet: Set[Int] = iterator.toSet
}

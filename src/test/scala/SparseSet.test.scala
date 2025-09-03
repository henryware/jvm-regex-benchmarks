/**
  */

package tests

import munit.ScalaCheckSuite
import org.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.Prop.{forAll,propBoolean}
import org.scalacheck.Arbitrary.arbitrary
import scala.collection.mutable
import scala.annotation.nowarn

import worldofregex.SparseSet

class SparseSetSpec extends ScalaCheckSuite {
  val maxSizeGen: Gen[Int] = Gen.choose(1, 1000)

  property("initial SparseSet is empty") {
    forAll(maxSizeGen) { maxSize =>
      val set = new SparseSet(maxSize)
      assertEquals(set.size, 0)
      assert(!(0 until maxSize).forall(set.contains))
    }
  }

  property("add increases size and contains returns true") {
    forAll(maxSizeGen, Gen.choose(0, 999)) { (maxSize, e) =>
      (maxSize > 0 && e >= 0 && e < maxSize) ==> {
        val set = new SparseSet(maxSize)
        assertEquals(set.add(e), true)
        assertEquals(set.size, 1)
        assertEquals(set.contains(e), true)
      }
    }
  }

  property("adding duplicate elements does nothing") {
    forAll(maxSizeGen, Gen.choose(0, 999)) { (maxSize, e) =>
      (maxSize > 0 && e >= 0 && e < maxSize) ==> {
        val set = new SparseSet(maxSize)
        assertEquals(set.add(e), true)
        assertEquals(set.add(e), false)
        assertEquals(set.size, 1)
      }
    }
  }

  property("remove decreases size and contains returns false") {
    forAll(maxSizeGen, Gen.choose(0, 999)) { (maxSize, e) =>
        (maxSize > 0 && e >= 0 && e < maxSize) ==> {
            val set = new SparseSet(maxSize)
            val _ =set.add(e)
            assertEquals(set.remove(e), true)
            assertEquals(set.size, 0)
            assertEquals(set.contains(e), false)
        }
    }
  }

  property("removing non-existent element does nothing") {
    forAll(maxSizeGen, Gen.choose(0, 999)) { (maxSize, e) =>
     (maxSize > 0 && e >= 0 && e < maxSize) ==> {
        val set = new SparseSet(maxSize)
        assertEquals(set.remove(e), false)
        assertEquals(set.size, 0)
      }
    }
  }

  property("SparseSet behaves like a Set under random operations") {
    forAll(maxSizeGen, Gen.listOf(arbitrary[Int])) { (maxSize, elements) =>
      val sparseSet = new SparseSet(maxSize)
      val model = mutable.Set.empty[Int]

      elements.foreach { e =>
        if (e >= 0 && e < maxSize) {
          val added = sparseSet.add(e)
          val modelAdded = model.add(e)
          assertEquals(added, modelAdded)
        } else {
          assertEquals(sparseSet.add(e), false)
          assertEquals(model.size, sparseSet.size)
        }
      }

      (0 until maxSize).foreach { e =>
        assertEquals(sparseSet.contains(e), model.contains(e))
      }

      assertEquals(sparseSet.toSet, model.toSet)

      elements.foreach { e =>
        if (e >= 0 && e < maxSize) {
          val removed = sparseSet.remove(e)
          val modelRemoved = model.remove(e)
          assertEquals(removed, modelRemoved)
        } else {
          assertEquals(sparseSet.remove(e), false)
          assertEquals(model.size, sparseSet.size)
        }
      }

      (0 until maxSize).foreach { e =>
        assertEquals(sparseSet.contains(e), model.contains(e))
      }

      assertEquals(sparseSet.toSet, model.toSet)
    }
  }

  property("clear resets the set") {
    forAll(maxSizeGen, Gen.listOf(Gen.choose(0, 999))) { (maxSize, elements) =>
      val validElements = elements.filter(e => e >= 0 && e < maxSize)
      val sparseSet = new SparseSet(maxSize)
      validElements.foreach(sparseSet.add)
      sparseSet.clear()
      assertEquals(sparseSet.size, 0)
      validElements.foreach(e => assertEquals(sparseSet.contains(e), false))
    }
  }

  property("iterator returns all elements") {
    forAll(maxSizeGen, Gen.listOf(Gen.choose(0, 999))) { (maxSize, elements) =>
      val validElements = elements.filter(e => e >= 0 && e < maxSize).distinct
      val sparseSet = new SparseSet(maxSize)
      validElements.foreach(sparseSet.add)
      assertEquals(sparseSet.toSet, validElements.toSet)
    }
  }
}

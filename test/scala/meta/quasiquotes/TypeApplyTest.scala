package scala.meta.quasiquotes

import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

/**
  * @author mutcianm
  * @since 24.10.16.
  */
@Category(Array(classOf[SlowTests]))
class TypeApplyTest extends QuasiQuoteTypeInferenceTestBase {

  def testStdNames(): Unit = doTest(
    s"""
      |${START}t"Int"$END
      |//Type.Name
    """.stripMargin
  )

  def testTypeApply(): Unit = doTest(
    s"""
       |${START}t"X[Y,Z]"$END
       |//Type.Apply
     """.stripMargin
  )

  def testTypeSelect(): Unit = doTest(
    s"""
      |${START}t"X.Y"$END
      |//Type.Select
    """.stripMargin
  )

  def testTypeProject(): Unit = doTest(
    s"""
       |${START}t"X#Y"$END
       |//Type.Project
     """.stripMargin
  )

  def testTypeSingleton(): Unit = doTest(
    s"""
       |${START}t"X.type"$END
       |//Type.Singleton
     """.stripMargin
  )

  def testTypeApplyInfix(): Unit = doTest(
    s"""
       |${START}t"X Y Z"$END
       |//Type.ApplyInfix
     """.stripMargin
  )

  def testFunctionType(): Unit = doTest(
    s"""
       |val atpes: List[Type.Arg] = List(t"X", t"Y")
       |val tpe = t"Z"
       |${START}t"(..$$atpes) => $$tpe"$END
       |//Type.Function
     """.stripMargin
  )

  def testTupleType(): Unit = doTest(
    s"""
       |val tpes = List(t"X", t"Y")
       |${START}t"(..$$tpes)"$END
       |//Type.Tuple
     """.stripMargin
  )

  def testExistentialType(): Unit = doTest(
    s"""
       |${START}t"X forSome { val a: A }"$END
       |//Type.Existential
     """.stripMargin
  )

  def testTypePlaceholder(): Unit = doTest(
    s"""
       |${START}t"_ >: X <: Y"$END
       |//Type.Placeholder
     """.stripMargin
  )

  def testTypeArgByName(): Unit = doTest(
    s"""
       |${START}targ"=> X"$END
       |//Type.Arg.ByName
     """.stripMargin
  )

  def testTypeArgRepeated(): Unit = doTest(
    s"""
       |${START}targ"X*"$END
       |//Type.Arg.Repeated
     """.stripMargin
  )

}

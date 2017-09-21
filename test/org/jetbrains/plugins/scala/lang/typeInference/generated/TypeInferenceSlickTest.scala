package org.jetbrains.plugins.scala.lang.typeInference.generated

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.libraryLoaders.{SlickLoader, ThirdPartyLibraryLoader}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 11/12/15
  */
@Category(Array(classOf[SlowTests]))
class TypeInferenceSlickTest extends TypeInferenceTestBase {
  //This class was generated by build script, please don't change this
  override def folderPath: String = super.folderPath + "slick/"

  override protected def additionalLibraries(): Array[ThirdPartyLibraryLoader] =
    Array(SlickLoader()(module))

  def testSCL9261(): Unit = doTest()

  def testSCL8829(): Unit = doTest()

  def testImplicitMacroTest(): Unit = doTest()
}


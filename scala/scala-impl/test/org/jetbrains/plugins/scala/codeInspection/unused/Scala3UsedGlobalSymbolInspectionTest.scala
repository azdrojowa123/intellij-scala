package org.jetbrains.plugins.scala.codeInspection.unused

import org.jetbrains.plugins.scala.ScalaVersion

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class Scala3UsedGlobalSymbolInspectionTest extends ScalaUnusedSymbolInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  private def addFile(text: String): Unit = myFixture.addFileToProject("Foo.scala", text)

  def test_extension_method(): Unit = {
    addFile("object Bar { import Foo.*; 0.plus0 }")
    checkTextHasNoErrors("object Foo { extension(i: Int) { def plus0: Int = i + 0 } }")
  }

  def test_enum(): Unit = {
    addFile("object Bar { import Foo.Language.*; Spanish match { case Spanish => } }")
    checkTextHasNoErrors("object Foo { enum Language { case Spanish } }")
  }

  def test_parameterized_enum(): Unit = {
    addFile("object Bar { import Foo.Fruit.*; Strawberry match { case s: Strawberry => s.i } }")
    checkTextHasNoErrors("object Foo { enum Fruit(val i: Int) { case Strawberry extends Fruit(42) } }")
  }

  def test_parameterized_enum_case(): Unit = {
    addFile("object Bar { import Foo.Fruit.*; Strawberry(42) match { case s: Strawberry => s.i } }")
    checkTextHasNoErrors("object Foo { enum Fruit { case Strawberry(i: Int) } }")
  }
}

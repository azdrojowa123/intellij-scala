package org.jetbrains.plugins.scala.refactoring.inline

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.refactoring.actions.BaseRefactoringAction
import com.intellij.refactoring.inline.GenericInlineHandler
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineHandler
import org.jetbrains.plugins.scala.refactoring.refactoringCommonTestDataRoot
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType}
import org.junit.Assert._

import java.io.File
import java.util.concurrent.TimeUnit

abstract class InlineRefactoringTestBase extends ScalaLightCodeInsightFixtureTestCase {
  protected val caretMarker = "/*caret*/"

  protected def folderPath = refactoringCommonTestDataRoot + "inline/"

  protected def doTest(): Unit = {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileText(ScalaFileType.INSTANCE, fileText)


    val offset = fileText.indexOf(caretMarker) + caretMarker.length
    assert(offset != -1, "Not specified caret marker in test case. Use /*caret*/ in scala file for this.")
    val editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContextFromFocusAsync.blockingGet(5, TimeUnit.SECONDS))
    editor.getCaretModel.moveToOffset(offset)

    val scalaFile = getFile.asInstanceOf[ScalaFile]
    var element = CommonDataKeys.PSI_ELEMENT.getData(DataManager.getInstance().getDataContextFromFocusAsync.blockingGet(5, TimeUnit.SECONDS))
    if (element == null){
      element = BaseRefactoringAction.getElementAtCaret(editor, scalaFile)
    }

    val firstPsi = scalaFile.findElementAt(0)
    val warning = firstPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT =>
        //noinspection DynamicPropertyKey
        ScalaBundle.message(firstPsi.getText.substring(2).trim)
      case _ => null
    }

    //start to inline
    try {
      executeWriteActionCommand("Test", UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION) {
        GenericInlineHandler.invoke(element, editor, new ScalaInlineHandler)
      }(getProject)
    }
    catch {
      case e: RefactoringErrorHintException =>
        assertEquals("Refactoring message don't match", warning, e.getMessage)
        return
      case e: Exception =>
        throw new AssertionError(e)
    }

    val ExpectedResultFromLastComment(res, expectedResult) = TestUtils.extractExpectedResultFromLastComment(scalaFile)
    assertEquals(expectedResult, res.replace(caretMarker, ""))
  }
}
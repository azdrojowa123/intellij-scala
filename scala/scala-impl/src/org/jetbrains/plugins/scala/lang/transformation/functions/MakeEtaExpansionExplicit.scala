package org.jetbrains.plugins.scala.lang.transformation.functions

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, ReferenceTarget}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.lang.transformation.AbstractTransformer
import org.jetbrains.plugins.scala.project.ProjectContext

class MakeEtaExpansionExplicit extends AbstractTransformer {
  override protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (e: ScReferenceExpression) && ReferenceTarget(_: ScFunction) &&
      NonValueType(_: ScMethodType) && ExpectedType(_: ScParameterizedType)
      if !e.getParent.isInstanceOf[ScUnderscoreSection] =>

      e.replace(code"$e _")

    case (e @ ScMethodCall(ReferenceTarget(_: ScFunction), _)) &&
      NonValueType(_: ScMethodType) && ExpectedType(_: ScParameterizedType)
      if !e.getParent.isInstanceOf[ScUnderscoreSection] =>

      e.replace(code"$e _")
  }
}

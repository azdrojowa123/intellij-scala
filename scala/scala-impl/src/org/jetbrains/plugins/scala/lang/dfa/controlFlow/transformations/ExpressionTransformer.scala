package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.psi.{PsiMethod, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.literalToDfType
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class ExpressionTransformer(val wrappedExpression: ScExpression)
  extends ScalaPsiElementTransformer(wrappedExpression) {

  override def toString: String = s"ExpressionTransformer: $wrappedExpression"

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = wrappedExpression match {
    case block: ScBlockExpr => transformBlock(block, builder)
    case parenthesised: ScParenthesisedExpr => transformParenthesisedExpression(parenthesised, builder)
    case invocation: MethodInvocation => transformInvocation(invocation, builder)
    case literal: ScLiteral => transformLiteral(literal, builder)
    case ifExpression: ScIf => transformIfExpression(ifExpression, builder)
    case reference: ScReferenceExpression => transformReference(reference, builder)
    case typedExpression: ScTypedExpression => transformTypedExpression(typedExpression, builder)
    case newTemplateDefinition: ScNewTemplateDefinition => transformNewTemplateDefinition(newTemplateDefinition, builder)
    case assignment: ScAssignment => transformAssignment(assignment, builder)
    case doWhileLoop: ScDo => transformDoWhileLoop(doWhileLoop, builder)
    case whileLoop: ScWhile => transformWhileLoop(whileLoop, builder)
    case forExpression: ScFor => transformForExpression(forExpression, builder)
    case matchExpression: ScMatch => transformMatchExpression(matchExpression, builder)
    case throwStatement: ScThrow => transformThrowStatement(throwStatement, builder)
    case returnStatement: ScReturn => transformReturnStatement(returnStatement, builder)
    case _: ScUnitExpr => builder.pushUnknownValue()
    case _: ScTuple => builder.pushUnknownValue()
    case _: ScTemplateDefinition => builder.pushUnknownValue()
    case otherExpression if isUnsupportedExpressionType(otherExpression) => builder.pushUnknownCall(otherExpression, 0)
    case _ => throw TransformationFailedException(wrappedExpression, "Unsupported expression.")
  }

  protected def transformExpression(expression: ScExpression, builder: ScalaDfaControlFlowBuilder): Unit = {
    new ExpressionTransformer(expression).transform(builder)
  }

  private def isUnsupportedExpressionType(expression: ScExpression): Boolean = {
    expression.is[ScTry, ScFunctionExpr, ScUnderscoreSection, ScGenericCall]
  }

  private def isReferenceExpressionInvocation(expression: ScReferenceExpression): Boolean = {
    expression.bind().map(_.element).exists(_.is[PsiMethod])
  }

  private def transformBlock(block: ScBlockExpr, builder: ScalaDfaControlFlowBuilder): Unit = {
    val statements = block.statements
    if (statements.isEmpty) {
      builder.pushUnknownValue()
    } else {
      statements.init.foreach { statement =>
        new ScalaPsiElementTransformer(statement).transform(builder)
        builder.popReturnValue()
      }

      transformPsiElement(statements.last, builder)
      builder.addInstruction(new FinishElementInstruction(block))
    }
  }

  private def transformParenthesisedExpression(parenthesised: ScParenthesisedExpr, builder: ScalaDfaControlFlowBuilder): Unit = {
    parenthesised.innerElement.foreach(transformExpression(_, builder))
  }

  private def transformLiteral(literal: ScLiteral, builder: ScalaDfaControlFlowBuilder): Unit = {
    if (literal.is[ScInterpolatedStringLiteral]) builder.pushUnknownCall(literal, 0)
    else builder.addInstruction(new PushValueInstruction(literalToDfType(literal), ScalaStatementAnchor(literal)))
  }

  private def transformIfExpression(ifExpression: ScIf, builder: ScalaDfaControlFlowBuilder): Unit = {
    val skipThenOffset = new DeferredOffset
    val skipElseOffset = new DeferredOffset

    transformIfPresent(ifExpression.condition, builder)
    builder.addInstruction(new ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, ifExpression.condition.orNull))

    builder.addInstruction(new FinishElementInstruction(null))
    transformIfPresent(ifExpression.thenExpression, builder)
    builder.addInstruction(new GotoInstruction(skipElseOffset))
    builder.setOffset(skipThenOffset)

    builder.addInstruction(new FinishElementInstruction(null))
    transformIfPresent(ifExpression.elseExpression, builder)
    builder.setOffset(skipElseOffset)

    builder.addInstruction(new FinishElementInstruction(ifExpression))
  }

  private def transformReference(expression: ScReferenceExpression, builder: ScalaDfaControlFlowBuilder): Unit = {
    if (isReferenceExpressionInvocation(expression)) {
      new InvocationTransformer(expression).transform(builder)
    } else {
      expression.qualifier.foreach { qualifier =>
        transformExpression(qualifier, builder)
        builder.popReturnValue()
      }

      ScalaDfaVariableDescriptor.fromReferenceExpression(expression) match {
        case Some(descriptor) => builder.pushVariable(descriptor, expression)
        case _ => builder.pushUnknownCall(expression, 0)
      }
    }
  }

  private def transformInvocation(invocationExpression: ScExpression, builder: ScalaDfaControlFlowBuilder): Unit = {
    new InvocationTransformer(invocationExpression).transform(builder)
  }

  private def transformTypedExpression(typedExpression: ScTypedExpression, builder: ScalaDfaControlFlowBuilder): Unit = {
    transformExpression(typedExpression.expr, builder)
  }

  private def transformNewTemplateDefinition(newTemplateDefinition: ScNewTemplateDefinition,
                                             builder: ScalaDfaControlFlowBuilder): Unit = {
    new InvocationTransformer(newTemplateDefinition).transform(builder)
  }

  private def transformAssignment(assignment: ScAssignment, builder: ScalaDfaControlFlowBuilder): Unit = {
    assignment.leftExpression match {
      case reference: ScReferenceExpression => reference.bind().map(_.element) match {
        case Some(element: PsiNamedElement) =>
          val descriptor = ScalaDfaVariableDescriptor(element, isStable = false)
          builder.assignVariableValue(descriptor, assignment.rightExpression)
        case _ => builder.pushUnknownCall(assignment, 0)
      }
      case _ => builder.pushUnknownCall(assignment, 0)
    }
  }

  private def transformDoWhileLoop(doWhileLoop: ScDo, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO implement transformation
    builder.pushUnknownCall(doWhileLoop, 0)
  }

  private def transformWhileLoop(whileLoop: ScWhile, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO implement transformation
    builder.pushUnknownCall(whileLoop, 0)
  }

  private def transformForExpression(forExpression: ScFor, builder: ScalaDfaControlFlowBuilder): Unit = {
    forExpression.desugared() match {
      case Some(desugared) => transformExpression(desugared, builder)
      case _ => builder.pushUnknownCall(forExpression, 0)
    }
  }

  private def transformMatchExpression(matchExpression: ScMatch, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO implement transformation
    builder.pushUnknownCall(matchExpression, 0)
  }

  private def transformThrowStatement(throwStatement: ScThrow, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO implement transformation
    throw TransformationFailedException(wrappedExpression, "Unsupported expression.")
  }

  private def transformReturnStatement(returnStatement: ScReturn, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO implement transformation
    throw TransformationFailedException(wrappedExpression, "Unsupported expression.")
  }
}

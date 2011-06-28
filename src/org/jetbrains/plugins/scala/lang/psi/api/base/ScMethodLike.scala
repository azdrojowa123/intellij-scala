package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import psi.types.ScType
import statements.params.ScTypeParamClause
import com.intellij.psi.PsiMethod
import toplevel.typedef.{ScTypeDefinition, ScMember}
import impl.ScalaPsiElementFactory
import caches.CachesUtil
import com.intellij.psi.util.PsiModificationTracker

/**
 * A member that can be converted to a ScMethodType, ie a method or a constructor.
 */
trait ScMethodLike extends ScMember { //todo: extends PsiMethod?
  def methodType: ScType = methodType(None)
  def methodType(result: Option[ScType]): ScType

  /**
   * This method is very important for generic type inference.
   * In case if we use just containg class type parameters
   * we can get problems about intersection of just class
   * type parameters and constructor type parameters. And
   * in that context it will have different meaning. See SCL-3095.
   * @return generated type parameters only for constructors
   */
  def getConstructorTypeParameters: Option[ScTypeParamClause] = {
    CachesUtil.get(this, CachesUtil.CONSTRUCTOR_TYPE_PARAMETERS_KEY,
      new CachesUtil.MyProvider[ScMethodLike, Option[ScTypeParamClause]](
        this, (value: ScMethodLike) => getConstructorTypeParametersImpl
      )(PsiModificationTracker.MODIFICATION_COUNT)
    )
  }

  private def getConstructorTypeParametersImpl: Option[ScTypeParamClause] = {
    this match {
      case method: PsiMethod if method.isConstructor =>
        val clazz = method.getContainingClass
        clazz match {
          case c: ScTypeDefinition =>
            c.typeParametersClause.map((typeParamClause: ScTypeParamClause) => {
              val paramClauseText = typeParamClause.getTextByStub
              ScalaPsiElementFactory.createTypeParameterClauseFromTextWithContext(paramClauseText,
                typeParamClause.getContext, typeParamClause)
            })
          case _ => None
        }
      case _ => None
    }
  }
}
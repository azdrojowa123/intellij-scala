package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.{AnnotatorPart, ScalaAnnotationHolder}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait

object TraitHasImplicitBound extends AnnotatorPart[ScTrait] {

  override def annotate(definition: ScTrait, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val contextBoundElements = definition.typeParameters.flatMap(p => p.contextBoundTypeElement)
    for (te <- contextBoundElements) {
      val message = ScalaBundle.message("traits.cannot.have.type.parameters.with.context.bounds")
      holder.createErrorAnnotation(te, message)
    }
    val viewBoundElements = definition.typeParameters.flatMap(p => p.viewTypeElement)
    for (te <- viewBoundElements) {
      val message = ScalaBundle.message("traits.cannot.have.type.parameters.with.view.bounds")
      holder.createErrorAnnotation(te, message)
    }
  }
}
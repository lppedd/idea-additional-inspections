package com.github.lppedd.inspections

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase
import com.siyeh.ig.psiutils.CommentTracker
import com.siyeh.ig.psiutils.ExpressionUtils

internal fun PsiElement.replaceWith(other: PsiElement): PsiElement =
  CommentTracker().replaceAndRestoreComments(this, other)

internal fun PsiClass.equalsToText(fqn: String) =
  fqn == qualifiedName

internal fun PsiType.isOneOf(fqn1: String, fqn2: String): Boolean =
  equalsToText(fqn1) || equalsToText(fqn2)

internal fun PsiType.isOneOf(vararg fqns: String): Boolean =
  fqns.any(this::equalsToText)

internal fun PsiTypeElement.shortenLike(other: PsiTypeElement) {
  if (other.isInferredType) {
    CodeStyleManager
      .getInstance(project)
      .reformat(IntroduceVariableBase.expandDiamondsAndReplaceExplicitTypeWithVar(this, this))
  } else {
    JavaCodeStyleManager
      .getInstance(project)
      .shortenClassReferences(this)
  }
}

internal fun PsiVariable.isEffectivelyFinal(): Boolean =
  if (hasModifierProperty(PsiModifier.FINAL)) {
    true
  } else {
    val scope = PsiUtil.getVariableCodeBlock(this, null)
    scope != null && HighlightControlFlowUtil.isEffectivelyFinal(
      this,
      scope,
      null
    )
  }

internal fun PsiMethod.getDeepestSuperMethodsClasses(): List<PsiClass?> =
  findDeepestSuperMethods().map { it.containingClass }

internal inline val PsiParameter.deepComponentType: PsiType
  get() = type.deepComponentType

internal inline val PsiExpression.passThroughParent: PsiElement?
  get() = ExpressionUtils.getPassThroughParent(this)

internal inline val PsiReferenceExpression.effectiveQualifier: PsiExpression?
  get() = ExpressionUtils.getEffectiveQualifier(this)

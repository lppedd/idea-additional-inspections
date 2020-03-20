package com.github.lppedd.inspections.unnecessaryCharSequenceToString

import com.github.lppedd.inspections.*
import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.*

private const val CHAR_SEQUENCE_FQN = "java.lang.CharSequence"

/**
 * @author Edoardo Luppi
 */
internal class UnnecessaryCharSequenceToStringFix(
  private val visitor: UnnecessaryCharSequenceToStringElementVisitor,
  psiVariable: PsiVariable
) : LocalQuickFix {
  private val smartVariable: SmartPsiElementPointer<PsiVariable> =
    SmartPointerManager
      .getInstance(psiVariable.project)
      .createSmartPsiElementPointer(psiVariable)

  override fun getName(): String =
    InspectionsBundle["unnecessary.charsequence.tostring.fix.description"]

  override fun getFamilyName(): String =
    CommonQuickFixBundle.message("fix.simplify")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val psiVariable = smartVariable.element!!

    if (visitor.checkVariable(psiVariable).isEmpty()) {
      return
    }

    val psiExpression = descriptor.psiElement.context as PsiExpression
    val psiMethodCallExpression = psiExpression.passThroughParent as PsiMethodCallExpression
    val qualifier = psiMethodCallExpression.methodExpression.effectiveQualifier as PsiExpression
    val newPsiMethodCallExpression = psiMethodCallExpression.replaceWith(qualifier) as PsiExpression

    // Replace the variable type with the method return type
    val charSeqTypeElement =
      PsiElementFactory
        .getInstance(project)
        .createTypeElementFromText(CHAR_SEQUENCE_FQN, newPsiMethodCallExpression)
    val oldTypeElement = psiVariable.typeElement as PsiTypeElement
    val newTypeElement = oldTypeElement.replaceWith(charSeqTypeElement) as PsiTypeElement
    newTypeElement.shortenLike(oldTypeElement)
  }
}

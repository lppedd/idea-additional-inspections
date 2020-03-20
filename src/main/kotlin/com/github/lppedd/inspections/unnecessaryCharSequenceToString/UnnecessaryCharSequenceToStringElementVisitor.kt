package com.github.lppedd.inspections.unnecessaryCharSequenceToString

import com.github.lppedd.inspections.*
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType.LIKE_UNUSED_SYMBOL
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.siyeh.ig.callMatcher.CallMatcher
import com.siyeh.ig.psiutils.ExpressionUtils
import java.util.stream.Stream
import kotlin.streams.toList

private const val STRING_FQN = "java.lang.String"
private const val CHAR_SEQUENCE_FQN = "java.lang.CharSequence"
private val TO_STRING_MATCHER: CallMatcher = CallMatcher.exactInstanceCall(CHAR_SEQUENCE_FQN, "toString")

/**
 * @author Edoardo Luppi
 */
internal class UnnecessaryCharSequenceToStringElementVisitor(
  private val problemsHolder: ProblemsHolder,
  private val isOnTheFly: Boolean
) : JavaElementVisitor() {
  override fun visitField(psiField: PsiField) {
    if (psiField.isPrivateAndFinal()) {
      ProgressIndicatorProvider.checkCanceled()
      addDescriptors(checkVariable(psiField))
    }
  }

  override fun visitLocalVariable(psiLocalVariable: PsiLocalVariable) {
    ProgressIndicatorProvider.checkCanceled()
    addDescriptors(checkVariable(psiLocalVariable))
  }

  private fun addDescriptors(descriptors: List<ProblemDescriptor>) {
    descriptors.forEach(problemsHolder::registerProblem)
  }

  fun checkVariable(psiVariable: PsiVariable): List<ProblemDescriptor> =
    if (psiVariable.type.isOneOf(STRING_FQN, CHAR_SEQUENCE_FQN) &&
        psiVariable.isEffectivelyFinal() &&
        psiVariable.isUsedAsCharSequenceOnly()) {
      checkVariableRecursive(psiVariable, psiVariable.initializer).toList()
    } else {
      emptyList()
    }

  private fun PsiVariable.isUsedAsCharSequenceOnly(): Boolean {
    // We search for all the usages of the variable.
    // If every method parameter where the variable is used as argument accepts
    // a CharSequence, then we can delete the toString() call
    val psiReferences =
      ReferencesSearch.search(this, useScope)
        .findAll()
        .filterIsInstance(PsiExpression::class.java)
        .toList()
        .ifEmpty {
          // No usages found so we can safely simplify code
          return true
        }

    for (psiVariableReference in psiReferences) {
      var varReferenceContext = psiVariableReference.context

      // The reference is used to call a method.
      // We need to check if that called method is owned by CharSequence or not
      if (varReferenceContext is PsiReferenceExpression) {
        val methodCallExpression = varReferenceContext.context as? PsiMethodCallExpression
        val method = methodCallExpression?.resolveMethod() ?: return false

        varReferenceContext =
          if (method.containingClass?.equalsToText(CHAR_SEQUENCE_FQN) == true) {
            // The called method is owned by CharSequence
            methodCallExpression
          } else {
            // The called method is not owned by CharSequence,
            // but it could have been overridden so we need to "walk" down to super
            if (!method.getDeepestSuperMethodsClasses().any { CHAR_SEQUENCE_FQN == it?.qualifiedName }) {
              return false
            }

            methodCallExpression.parent
          }
      }

      // The reference is used inside a method call, someVar.someMethod(|varRef|, ...)
      // We need to check if the corresponding method's parameter is of type CharSequence
      if (varReferenceContext is PsiExpressionList &&
          (varReferenceContext.context as? PsiMethodCallExpression)
            ?.getParameter(psiVariableReference)
            ?.deepComponentType
            ?.equalsToText(CHAR_SEQUENCE_FQN) == false) {
        return false
      }
    }

    return true
  }

  private fun checkVariableRecursive(
    variable: PsiVariable,
    initializer: PsiExpression?
  ): Stream<ProblemDescriptor> {
    return if (initializer == null) Stream.empty()
    else ExpressionUtils
      .nonStructuralChildren(initializer)
      .filterIsInstance(PsiMethodCallExpression::class.java)
      .flatMap { psiMethodCallExpression ->
        val psiMethodExpression = psiMethodCallExpression.methodExpression
        val psiQualifierExpression = psiMethodExpression.qualifierExpression
        val childProblems = checkVariableRecursive(variable, psiQualifierExpression)

        if (TO_STRING_MATCHER.test(psiMethodCallExpression)) {
          Stream.of(buildProblem(variable, psiMethodExpression)) + childProblems
        } else {
          childProblems
        }
      }
  }

  private fun buildProblem(
    variable: PsiVariable,
    methodExpression: PsiReferenceExpression
  ): ProblemDescriptor =
    problemsHolder.manager.createProblemDescriptor(
      methodExpression.referenceNameElement!!,
      InspectionsBundle["unnecessary.charsequence.tostring.displayName"],
      true,
      LIKE_UNUSED_SYMBOL,
      isOnTheFly,
      UnnecessaryCharSequenceToStringFix(this, variable)
    )
}

private fun PsiField.isPrivateAndFinal(): Boolean =
  modifierList?.run {
    hasModifierProperty(PsiModifier.PRIVATE) &&
        hasModifierProperty(PsiModifier.FINAL)
  } ?: false

private fun PsiExpression.argumentIndex(): Int {
  return (parent as? PsiExpressionList ?: return -1).expressions.indexOf(this)
}

private fun PsiMethodCallExpression.getParameter(psiExpression: PsiExpression): PsiParameter? {
  val method = resolveMethod() ?: return null
  val parameters = method.parameterList.parameters
  val argumentIndex = psiExpression.argumentIndex()

  return when {
    // Something wrong?
    argumentIndex < 0 -> null
    // Argument passed to vararg method's parameter
    argumentIndex > parameters.lastIndex && method.isVarArgs -> parameters.lastOrNull()
    // Argument passed to normal method's parameter
    else -> parameters[argumentIndex]
  }
}

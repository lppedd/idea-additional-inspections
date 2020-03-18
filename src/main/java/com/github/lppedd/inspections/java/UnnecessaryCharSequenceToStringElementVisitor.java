package com.github.lppedd.inspections.java;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import com.github.lppedd.inspections.InspectionsBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author Edoardo Luppi
 */
class UnnecessaryCharSequenceToStringElementVisitor extends JavaElementVisitor {
  private static final String CHAR_SEQUENCE_FQN = "java.lang.CharSequence";
  private static final String STRING_FQN = "java.lang.String";

  private final @NotNull ProblemsHolder holder;
  private final boolean isOnTheFly;
  private final @NotNull PsiClassType charSequenceType;
  private final @NotNull PsiClassType stringType;
  private final @NotNull PsiElementFactory elementFactory;

  UnnecessaryCharSequenceToStringElementVisitor(
      final @NotNull ProblemsHolder holder,
      final boolean isOnTheFly) {
    this.holder = holder;
    this.isOnTheFly = isOnTheFly;

    elementFactory = JavaPsiFacade.getElementFactory(holder.getProject());
    charSequenceType = elementFactory.createTypeByFQClassName(CHAR_SEQUENCE_FQN);
    stringType = elementFactory.createTypeByFQClassName(STRING_FQN);
  }

  @Override
  public void visitLocalVariable(final @NotNull PsiLocalVariable variable) {
    ProgressIndicatorProvider.checkCanceled();
    addDescriptors(checkVariable(variable));
  }

  private void addDescriptors(final @NotNull ProblemDescriptor[] descriptors) {
    for (final var descriptor : descriptors) {
      holder.registerProblem(descriptor);
    }
  }

  public @NotNull ProblemDescriptor[] checkVariable(final @NotNull PsiVariable variable) {
    final var variableType = variable.getType();

    // We consider only variables which are final, and of type CharSequence/String
    // (because we are looking for toString() calls and a String can be assigned
    // only to a String or CharSequence)
    if (!variable.hasModifierProperty(PsiModifier.FINAL) ||
        !stringType.equals(variableType) &&
        !charSequenceType.equals(variableType) ||
        !shouldSearchToStringCalls(variable)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    // We need to check only the initialization point
    final var initializer = variable.getInitializer();

    return initializer instanceof PsiMethodCallExpression
        ? checkMethodCallExpression(variable, (PsiMethodCallExpression) initializer)
        : ProblemDescriptor.EMPTY_ARRAY;
  }

  private @NotNull ProblemDescriptor[] checkMethodCallExpression(
      final @NotNull PsiVariable variable,
      final @NotNull PsiMethodCallExpression psiMethodCallExpression) {
    final var method = Objects.requireNonNull(psiMethodCallExpression.resolveMethod());
    final var containingClass = Objects.requireNonNull(method.getContainingClass());
    final var containingType = elementFactory.createType(containingClass);

    if (!"toString".equals(method.getName()) || !charSequenceType.isAssignableFrom(containingType)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    final var referenceNameElement =
        psiMethodCallExpression.getMethodExpression()
                               .getReferenceNameElement();

    if (referenceNameElement == null) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    final var problemDescriptor = holder.getManager().createProblemDescriptor(
        referenceNameElement,
        InspectionsBundle.get("unnecessary.charsequence.tostring.displayName"),
        true,
        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
        isOnTheFly,
        new UnnecessaryCharSequenceToStringFix(this, variable)
    );

    return new ProblemDescriptor[] {problemDescriptor};
  }

  private boolean shouldSearchToStringCalls(final @NotNull PsiVariable variable) {
    // We search for all the usages of the variable.
    // If every method parameter where the variable is used as argument accepts
    // a CharSequence, then we can delete the toString() call
    final var psiReferences = ReferencesSearch.search(variable, variable.getUseScope()).findAll();

    // No usages found
    if (psiReferences.isEmpty()) {
      return false;
    }

    for (final var psiReference : psiReferences) {
      final var psiReferenceElement = psiReference.getElement();
      final var psiMethodCallExpression = PsiTreeUtil.getContextOfType(
          psiReferenceElement,
          PsiMethodCallExpression.class,
          false,
          PsiStatement.class
      );

      if (psiMethodCallExpression != null &&
          !isMethodParameterCharSequence(psiReference, psiMethodCallExpression)) {
        return false;
      }
    }

    return true;
  }

  private boolean isMethodParameterCharSequence(
      final @NotNull PsiReference psiReference,
      final @NotNull PsiMethodCallExpression psiMethodCallExpression) {
    final var argumentList = psiMethodCallExpression.getArgumentList();
    final var argumentIndex = getArgumentIndex(argumentList.getExpressions(), psiReference);
    final var psiMethod = Objects.requireNonNull(psiMethodCallExpression.resolveMethod());
    final var psiParameters = psiMethod.getParameterList().getParameters();

    // Argument might have been passed to a vararg parameter
    if (psiParameters.length <= argumentIndex) {
      final var psiParameter = psiParameters[psiParameters.length - 1];
      final var parameterType = psiParameter.getType().getDeepComponentType();
      return psiParameter.isVarArgs() && charSequenceType.equals(parameterType);
    }

    final var parameterType = psiParameters[argumentIndex].getType().getDeepComponentType();
    return charSequenceType.equals(parameterType);
  }

  private static int getArgumentIndex(
      final @NotNull PsiExpression[] psiExpressions,
      final @NotNull PsiReference psiReference) {
    for (var i = 0; i < psiExpressions.length; i++) {
      if (psiExpressions[i] == psiReference) {
        return i;
      }
    }

    throw new IllegalStateException(/* TODO */);
  }
}

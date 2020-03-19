package com.github.lppedd.inspections.unnecessaryCharSequenceToString;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.lppedd.inspections.InspectionsBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;

/**
 * @author Edoardo Luppi
 */
class UnnecessaryCharSequenceToStringElementVisitor extends JavaElementVisitor {
  private static final String CHAR_SEQUENCE_FQN = "java.lang.CharSequence";
  private static final String STRING_FQN = "java.lang.String";
  private static final CallMatcher TO_STRING =
      CallMatcher.exactInstanceCall(CHAR_SEQUENCE_FQN, "toString");

  private final @NotNull ProblemsHolder problemsHolder;
  private final boolean isOnTheFly;

  UnnecessaryCharSequenceToStringElementVisitor(
      final @NotNull ProblemsHolder problemsHolder,
      final boolean isOnTheFly) {
    this.problemsHolder = problemsHolder;
    this.isOnTheFly = isOnTheFly;
  }

  @Override
  public void visitField(final @NotNull PsiField psiField) {
    if (psiField.hasModifierProperty(PsiModifier.PRIVATE)) {
      ProgressIndicatorProvider.checkCanceled();
      addDescriptors(checkVariable(psiField));
    }
  }

  @Override
  public void visitLocalVariable(final @NotNull PsiLocalVariable psiLocalVariable) {
    ProgressIndicatorProvider.checkCanceled();
    addDescriptors(checkVariable(psiLocalVariable));
  }

  private void addDescriptors(final @NotNull ProblemDescriptor[] descriptors) {
    for (final var descriptor : descriptors) {
      problemsHolder.registerProblem(descriptor);
    }
  }

  @NotNull ProblemDescriptor[] checkVariable(final @NotNull PsiVariable psiVariable) {
    // We consider only variables which are final or never reassigned,
    // and of type CharSequence/String (because we are looking for toString() calls
    // and a String can be assigned only to a String or CharSequence)
    if (isVariableTypeStringOrCharSequence(psiVariable.getType()) &&
        isVariableEffectivelyFinal(psiVariable) &&
        shouldSearchForToStringCalls(psiVariable)) {
      final var initializer = psiVariable.getInitializer();
      return checkVariableRecursive(psiVariable, initializer).toArray(ProblemDescriptor[]::new);
    }

    return ProblemDescriptor.EMPTY_ARRAY;
  }

  private @NotNull Stream<ProblemDescriptor> checkVariableRecursive(
      final @NotNull PsiVariable variable,
      final @Nullable PsiExpression initializer) {
    // noinspection SimplifiableIfStatement
    if (initializer == null) {
      return Stream.empty();
    }

    return ExpressionUtils
        .nonStructuralChildren(initializer)
        .filter(PsiMethodCallExpression.class::isInstance)
        .map(PsiMethodCallExpression.class::cast)
        .flatMap(psiMethodCallExpression -> {
          final var psiMethodExpression = psiMethodCallExpression.getMethodExpression();
          final var psiQualifierExpression = psiMethodExpression.getQualifierExpression();
          final var childProblems = checkVariableRecursive(variable, psiQualifierExpression);
          return TO_STRING.test(psiMethodCallExpression)
              ? Stream.concat(Stream.of(buildProblem(variable, psiMethodExpression)), childProblems)
              : childProblems;
        });
  }

  private @NotNull ProblemDescriptor buildProblem(
      final @NotNull PsiVariable variable,
      final @NotNull PsiReferenceExpression methodExpression) {
    return problemsHolder.getManager().createProblemDescriptor(
        Objects.requireNonNull(methodExpression.getReferenceNameElement()),
        InspectionsBundle.get("unnecessary.charsequence.tostring.displayName"),
        true,
        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
        isOnTheFly,
        new UnnecessaryCharSequenceToStringFix(this, variable)
    );
  }

  private static boolean isVariableTypeStringOrCharSequence(final PsiType psiVariableType) {
    return TypeUtils.typeEquals(STRING_FQN, psiVariableType) ||
           TypeUtils.typeEquals(CHAR_SEQUENCE_FQN, psiVariableType);
  }

  private static boolean isVariableEffectivelyFinal(final @NotNull PsiVariable psiVariable) {
    final var hasFinalModifier = psiVariable.hasModifierProperty(PsiModifier.FINAL);

    if (hasFinalModifier) {
      return true;
    }

    if (psiVariable instanceof PsiField) {
      return false;
    }

    final var scope = PsiUtil.getVariableCodeBlock(psiVariable, null);
    return scope != null && HighlightControlFlowUtil.isEffectivelyFinal(psiVariable, scope, null);
  }

  private static boolean shouldSearchForToStringCalls(final @NotNull PsiVariable psiVariable) {
    // We search for all the usages of the variable.
    // If every method parameter where the variable is used as argument accepts
    // a CharSequence, then we can delete the toString() call
    final var psiReferences =
        ReferencesSearch.search(psiVariable, psiVariable.getUseScope())
                        .findAll()
                        .stream()
                        .filter(PsiExpression.class::isInstance)
                        .map(PsiExpression.class::cast)
                        .collect(Collectors.toList());

    if (psiReferences.isEmpty()) {
      // No usages found so we can safely try to simplify code
      return true;
    }

    for (final var psiVariableReference : psiReferences) {
      var psiVariableReferenceContext = psiVariableReference.getContext();

      if (psiVariableReferenceContext instanceof PsiReferenceExpression psiReferenceExpression &&
          psiReferenceExpression.getContext() instanceof PsiMethodCallExpression psiMethodCallExpr) {
        // The variable is used to call a method, e.g. myVar.someMethod()
        // We need to check if the method is inherited from CharSequence,
        // and only in this case allow removing toString()
        final var psiMethod = psiMethodCallExpr.resolveMethod();

        if (psiMethod == null) {
          continue;
        }

        final var psiMethodContainingClass = Objects.requireNonNull(psiMethod.getContainingClass());

        if (CHAR_SEQUENCE_FQN.equals(psiMethodContainingClass.getQualifiedName())) {
          // The variable is used as CharSequence
          psiVariableReferenceContext = psiMethodCallExpr.getParent();
        } else {
          final var superMethods = psiMethod.findDeepestSuperMethods();

          if (superMethods.length == 0) {
            if (!CHAR_SEQUENCE_FQN.equals(psiMethodContainingClass.getQualifiedName())) {
              return false;
            }
          } else {
            final var containingClass = Objects.requireNonNull(superMethods[0].getContainingClass());

            if (!CHAR_SEQUENCE_FQN.equals(containingClass.getQualifiedName())) {
              return false;
            }

            psiVariableReferenceContext = psiMethodCallExpr.getParent();
          }
        }
      }

      if (psiVariableReferenceContext instanceof PsiExpressionList psiExpressionList &&
          psiExpressionList.getContext() instanceof PsiMethodCallExpression psiMethodCallExpr &&
          !isMethodParameterCharSequence(psiVariableReference, psiMethodCallExpr)) {
        return false;
      }
    }

    return true;
  }

  private static boolean isMethodParameterCharSequence(
      final @NotNull PsiExpression psiArgumentExpression,
      final @NotNull PsiMethodCallExpression psiMethodCallExpression) {
    final var parameterForArgument = MethodCallUtils.getParameterForArgument(psiArgumentExpression);

    if (parameterForArgument != null) {
      final var parameterType = parameterForArgument.getType().getDeepComponentType();
      return TypeUtils.typeEquals(CHAR_SEQUENCE_FQN, parameterType);
    }

    // Might be a vararg argument
    final var psiVarargMethod = psiMethodCallExpression.resolveMethod();

    if (psiVarargMethod == null) {
      return false;
    }

    final var psiParameters = psiVarargMethod.getParameterList().getParameters();

    if (psiParameters.length == 0) {
      return false;
    }

    final var psiParameter = psiParameters[psiParameters.length - 1];
    return psiParameter.isVarArgs() &&
           TypeUtils.typeEquals(CHAR_SEQUENCE_FQN, psiParameter.getType().getDeepComponentType());
  }
}

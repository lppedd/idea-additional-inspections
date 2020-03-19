package com.github.lppedd.inspections.unnecessaryCharSequenceToString;

import static com.intellij.refactoring.introduceVariable.IntroduceVariableBase.expandDiamondsAndReplaceExplicitTypeWithVar;

import org.jetbrains.annotations.NotNull;

import com.github.lppedd.inspections.InspectionsBundle;
import com.github.lppedd.inspections.Utils;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;

/**
 * @author Edoardo Luppi
 */
class UnnecessaryCharSequenceToStringFix implements LocalQuickFix {
  private final @NotNull UnnecessaryCharSequenceToStringElementVisitor visitor;
  private final @NotNull SmartPsiElementPointer<PsiVariable> smartVariable;

  UnnecessaryCharSequenceToStringFix(
      final @NotNull UnnecessaryCharSequenceToStringElementVisitor visitor,
      final @NotNull PsiVariable psiVariable) {
    this.visitor = visitor;
    smartVariable =
        SmartPointerManager.getInstance(psiVariable.getProject())
                           .createSmartPsiElementPointer(psiVariable);
  }

  @Override
  public @NotNull String getName() {
    return InspectionsBundle.get("unnecessary.charsequence.tostring.fix.description");
  }

  @Override
  public @NotNull String getFamilyName() {
    return CommonQuickFixBundle.message("fix.simplify");
  }

  @Override
  public void applyFix(
      final @NotNull Project project,
      final @NotNull ProblemDescriptor descriptor) {
    final var psiVariable = smartVariable.getElement();

    if (psiVariable == null) {
      throw new IllegalStateException("The variable PsiElement cannot be null");
    }

    if (visitor.checkVariable(psiVariable).length == 0) {
      return;
    }

    final var psiExpression = Utils.castOrFail(
        descriptor.getPsiElement().getContext(),
        PsiExpression.class
    );

    final var psiMethodCallExpression = Utils.castOrFail(
        ExpressionUtils.getPassThroughParent(psiExpression),
        PsiMethodCallExpression.class
    );

    final var psiMethodExpression = psiMethodCallExpression.getMethodExpression();
    final var qualifier = ExpressionUtils.getEffectiveQualifier(psiMethodExpression);

    if (qualifier == null) {
      return;
    }

    final var newPsiMethodCallExpression = Utils.castOrFail(
        new CommentTracker().replaceAndRestoreComments(psiMethodCallExpression, qualifier),
        PsiExpression.class
    );

    // noinspection ConstantConditions
    final var newTypeElement =
        JavaPsiFacade.getElementFactory(project)
                     .createTypeElement(newPsiMethodCallExpression.getType());

    final var typeElement = psiVariable.getTypeElement();

    // Replace the variable type with the method return type
    // noinspection ConstantConditions
    final var newType = Utils.castOrFail(
        new CommentTracker().replaceAndRestoreComments(typeElement, newTypeElement),
        PsiTypeElement.class
    );

    if (typeElement.isInferredType()) {
      CodeStyleManager.getInstance(project)
                      .reformat(expandDiamondsAndReplaceExplicitTypeWithVar(newType, newType));
    } else {
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(newType);
    }
  }
}

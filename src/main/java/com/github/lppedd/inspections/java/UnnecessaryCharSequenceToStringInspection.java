package com.github.lppedd.inspections.java;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;

/**
 * @author Edoardo Luppi
 */
class UnnecessaryCharSequenceToStringInspection
    extends LocalInspectionTool
    implements CleanupLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(
      final @NotNull ProblemsHolder holder,
      final boolean isOnTheFly) {
    return new UnnecessaryCharSequenceToStringElementVisitor(holder, isOnTheFly);
  }
}

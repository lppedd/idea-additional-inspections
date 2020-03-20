package com.github.lppedd.inspections.unnecessaryCharSequenceToString

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor

/**
 * @author Edoardo Luppi
 */
private class UnnecessaryCharSequenceToStringInspection
  : LocalInspectionTool(),
    CleanupLocalInspectionTool {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    UnnecessaryCharSequenceToStringElementVisitor(holder, isOnTheFly)
}

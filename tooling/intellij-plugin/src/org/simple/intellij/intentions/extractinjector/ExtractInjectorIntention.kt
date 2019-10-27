package org.simple.intellij.intentions.extractinjector

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class ExtractInjectorIntention : PsiElementBaseIntentionAction(), IntentionAction {

  private val logger = Logger.getInstance("ExtractDaggerInjector")

  override fun getFamilyName(): String {
    return "Extract injector interface"
  }

  override fun getText(): String {
    return "Extract injection method into an external interface"
  }

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    logger.debug("IS AVAILABLE?")
    return true
  }

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    logger.debug("INVOKE!")
  }
}

// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.lambda;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class RedundantLambdaParameterTypeInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(RedundantLambdaParameterTypeInspection.class);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitParameterList(PsiParameterList parameterList) {
        super.visitParameterList(parameterList);
        if (isApplicable(parameterList)) {
          holder.registerProblem(parameterList, "Lambda parameter type is redundant", new LambdaParametersFix());
        }
      }
    };
  }

  private static boolean isApplicable(@NotNull PsiParameterList parameterList) {
    final PsiElement parent = parameterList.getParent();
    if (!(parent instanceof PsiLambdaExpression)) return false;
    final PsiLambdaExpression expression = (PsiLambdaExpression)parent;
    final PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      if (parameter.getTypeElement() == null) return false;
    }
    if (parameters.length == 0) return false;
    final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    if (functionalInterfaceType != null) {
      final PsiElement lambdaParent = expression.getParent();
      if (lambdaParent instanceof PsiExpressionList) {
        final PsiElement gParent = lambdaParent.getParent();
        if (gParent instanceof PsiCallExpression && ((PsiCallExpression)gParent).getTypeArguments().length == 0) {
          final JavaResolveResult resolveResult = ((PsiCallExpression)gParent).resolveMethodGenerics();
          final PsiMethod method = (PsiMethod)resolveResult.getElement();
          if (method == null) return false;
          final int idx = LambdaUtil.getLambdaIdx((PsiExpressionList)lambdaParent, expression);
          if (idx < 0) return false;

          PsiCallExpression copy = (PsiCallExpression)gParent.copy();
          PsiLambdaExpression lambdaToStripTypeParameters = (PsiLambdaExpression)copy.getArgumentList().getExpressions()[idx];
          for (PsiParameter parameter : lambdaToStripTypeParameters.getParameterList().getParameters()) {
            parameter.getTypeElement().delete();
          }

          return functionalInterfaceType.equals(lambdaToStripTypeParameters.getFunctionalInterfaceType());
        }
      }
      return true;
    }
    return false;
  }

  private static void removeTypes(PsiLambdaExpression lambdaExpression) {
    if (lambdaExpression != null) {
      final PsiParameter[] parameters = lambdaExpression.getParameterList().getParameters();
      final String text;
      if (parameters.length == 1) {
        text = parameters[0].getName();
      }
      else {
        text = "(" + StringUtil.join(parameters, PsiParameter::getName, ", ") + ")";
      }
      final PsiLambdaExpression expression = (PsiLambdaExpression)JavaPsiFacade.getElementFactory(lambdaExpression.getProject())
        .createExpressionFromText(text + "->{}", lambdaExpression);
      CommentTracker tracker = new CommentTracker();
      tracker.replaceAndRestoreComments(lambdaExpression.getParameterList(), expression.getParameterList());
    }
  }

  private static class LambdaParametersFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Remove redundant parameter types";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiLambdaExpression) {
        removeTypes((PsiLambdaExpression)parent);
      }
    }
  }
}

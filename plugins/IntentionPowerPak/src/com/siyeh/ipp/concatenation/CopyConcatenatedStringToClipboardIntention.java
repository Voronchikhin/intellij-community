/*
 * Copyright 2008-2011 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.concatenation;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public class CopyConcatenatedStringToClipboardIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new SimpleStringConcatenationPredicate(false);
  }

  @Override
  protected void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    if (!(element instanceof PsiPolyadicExpression)) {
      return;
    }
    PsiPolyadicExpression concatenationExpression =
      (PsiPolyadicExpression)element;
    final IElementType tokenType =
      concatenationExpression.getOperationTokenType();
    if (tokenType != JavaTokenType.PLUS) {
      return;
    }
    final PsiType type = concatenationExpression.getType();
    if (type == null || !type.equalsToText("java.lang.String")) {
      return;
    }
    final StringBuilder text = new StringBuilder();
    buildConcatenationText(concatenationExpression, text);
    final Transferable contents = new StringSelection(text.toString());
    CopyPasteManager.getInstance().setContents(contents);
  }

  private static void buildConcatenationText(PsiPolyadicExpression expression,
                                             StringBuilder out) {
    for (PsiExpression operand : expression.getOperands()) {
      final Object value =
        ExpressionUtils.computeConstantExpression(operand);
      if (value == null) {
        out.append('?');
      }
      else {
        out.append(value.toString());
      }
    }
  }
}

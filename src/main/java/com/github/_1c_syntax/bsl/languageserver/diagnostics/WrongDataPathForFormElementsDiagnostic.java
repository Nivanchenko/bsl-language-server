/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.mdo.children.Form;
import com.github._1c_syntax.mdclasses.mdo.children.form.FormItem;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.support.ScriptVariant;
import org.eclipse.lsp4j.Range;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 5,
  scope = DiagnosticScope.BSL,
  modules = {
    // todo переделать, когда появится привязка к объектам метаданных
    ModuleType.ManagedApplicationModule
  },
  tags = {
    DiagnosticTag.UNPREDICTABLE,
  }

)
public class WrongDataPathForFormElementsDiagnostic extends AbstractDiagnostic {

  private Range diagnosticRange;

  private static boolean wrongDataPath(FormItem formItem) {
    return formItem.getDataPath().getSegment().startsWith("~");
  }

  @Override
  protected void check() {

    Ranges.getFirstSignificantTokenRange(documentContext.getTokens())
      .ifPresent(this::checkAllForms);
  }

  private void checkAllForms(Range range) {
    diagnosticRange = range;

    documentContext.getServerContext().getConfiguration().getChildrenByMdoRef().values().stream()
      .filter(Form.class::isInstance)
      .map(Form.class::cast)
      .forEach(this::checkForm);
  }

  private void checkForm(Form form) {
    var range = getModuleOrCommonRange(form);

    form.getData().getPlainChildren().stream()
      .filter(WrongDataPathForFormElementsDiagnostic::wrongDataPath)
      .forEach(formItem -> diagnosticStorage.addDiagnostic(range,
        info.getMessage(formItem.getName(), getMdoRef(form))));
  }

  private Range getModuleOrCommonRange(Form form) {
    return documentContext.getServerContext()
      .getDocument(form.getMdoReference().getMdoRef(), ModuleType.FormModule)
      .flatMap(docCtx -> docCtx.getSymbolTree().getChildrenFlat().stream()
        .findFirst()
        .map(SourceDefinedSymbol::getSelectionRange))
      .orElse(diagnosticRange);
  }

  private String getMdoRef(Form form) {
    if (documentContext.getServerContext().getConfiguration().getScriptVariant() == ScriptVariant.ENGLISH) {
      return form.getMdoReference().getMdoRef();
    }
    return form.getMdoReference().getMdoRefRu();
  }
}

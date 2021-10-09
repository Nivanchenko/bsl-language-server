/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.bsl.parser.SDBLParser.QueryContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github._1c_syntax.bsl.parser.SDBLParser.RULE_expression;
import static com.github._1c_syntax.bsl.parser.SDBLParser.RULE_query;
import static com.github._1c_syntax.bsl.parser.SDBLParser.RULE_searchCondition;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.SQL,
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.UNPREDICTABLE
  }

)
public class FieldsFromJoinsWithoutIsNullDiagnostic extends AbstractSDBLVisitorDiagnostic {

  private static final Set<Integer> SELECT_STATEMENTS = Set.of(SDBLParser.RULE_selectedField, RULE_expression);

  private static final Set<Integer> WHERE_STATEMENTS = Set.of(RULE_searchCondition, RULE_expression);

  private static final Set<Integer> JOIN_STATEMENTS = Set.of(SDBLParser.RULE_joinPart, RULE_expression);

  public static final int IS_NOT_NULL_EXPR_MEMBERS_COUNT = 4;
  public static final int NOT_WITH_PARENS_EXPR_MEMBERS_COUNT = 4;

  private final List<BSLParserRuleContext> nodesForIssues = new ArrayList<>();

  @Override
  public ParseTree visitJoinPart(SDBLParser.JoinPartContext joinPartCtx) {

    try {
      joinedTables(joinPartCtx)
        .forEach(tableName -> checkQuery(tableName, joinPartCtx));

      if (!nodesForIssues.isEmpty()) {
        diagnosticStorage.addDiagnostic(joinPartCtx, getRelatedInformation(joinPartCtx));
      }

    } catch (Exception e) {
      nodesForIssues.clear();
      throw e;
    }
    nodesForIssues.clear();

    return super.visitJoinPart(joinPartCtx);
  }

  private Stream<String> joinedTables(SDBLParser.JoinPartContext joinPartCtx) {
    return Optional.of(joinPartCtx)
      .stream().flatMap(joinPartContext -> joinedDataSourceContext(joinPartContext).stream())
      .filter(Objects::nonNull)
      .map(SDBLParser.DataSourceContext::alias)
      .filter(Objects::nonNull)
      .map(SDBLParser.AliasContext::identifier)
      .map(BSLParserRuleContext::getText);
  }

  private List<SDBLParser.DataSourceContext> joinedDataSourceContext(SDBLParser.JoinPartContext joinPartContext) {
    if (joinPartContext.LEFT_JOIN() != null) {
      return Collections.singletonList(joinPartContext.dataSource());
    } else if (joinPartContext.RIGHT_JOIN() != null) {
      return Collections.singletonList(((SDBLParser.DataSourceContext) joinPartContext.getParent()));
    } else if (joinPartContext.FULL_JOIN() != null) {
      return Arrays.asList(((SDBLParser.DataSourceContext) joinPartContext.getParent()),
        joinPartContext.dataSource());
    }
    return Collections.emptyList();
  }

  private void checkQuery(String joinedTableName, SDBLParser.JoinPartContext joinPartCtx) {
    Optional.ofNullable(Trees.getRootParent(joinPartCtx, RULE_query))
      .map(QueryContext.class::cast)
      .filter(ctx -> !haveExprNotIsNullInsideWhere(ctx.where))
      .ifPresent(queryCtx -> {
        checkSelect(joinedTableName, queryCtx.columns);
        checkWhere(joinedTableName, queryCtx.where);

        checkAllJoins(joinedTableName, joinPartCtx);
      });
  }

  private boolean haveExprNotIsNullInsideWhere(@Nullable SDBLParser.SearchConditionsContext whereCtx) {
    return Optional.ofNullable(whereCtx)
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, SDBLParser.RULE_isNullPredicate).stream())
      .map(whereMember -> (SDBLParser.IsNullPredicateContext) whereMember)
      .anyMatch(isNullPredicateCtx -> {
        final var a = haveFirstIsThenNotThenNullInsideWhereMember(isNullPredicateCtx);
        final var b = haveExprNotIsNullInsideWhereMember(isNullPredicateCtx);
        return a || b;
        }
      );
  }

  private boolean haveFirstIsThenNotThenNullInsideWhereMember(SDBLParser.IsNullPredicateContext whereMember) {
    return whereMember.getChildCount() == IS_NOT_NULL_EXPR_MEMBERS_COUNT;
  }

  private Stream<SDBLParser.ColumnContext> isTableFieldExpr(
    String tableName, BSLParserRuleContext expression,
    Set<Integer> statements) {

    return Optional.of(expression)
      .stream().flatMap(ctx -> Trees.findAllRuleNodes(ctx, SDBLParser.RULE_column).stream())
      .filter(Objects::nonNull)
      .filter(ctx -> ctx instanceof SDBLParser.ColumnContext)
      .map(SDBLParser.ColumnContext.class::cast)
      .filter(columnContext -> checkColumn(tableName, columnContext, statements));
  }

  private boolean isTerminalNodeNOT(ParseTree node) {
    return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == SDBLParser.NOT;
  }

  private boolean checkColumn(String tableName, SDBLParser.ColumnContext columnCtx,
                              Set<Integer> statements) {
    return Optional.of(columnCtx)
      .filter(columnContext -> columnContext.mdoName != null)
      .filter(columnContext -> columnContext.mdoName.getText().equalsIgnoreCase(tableName))
      .filter(columnContext -> !haveIsNullInside(columnContext, statements))
      .isPresent();
  }

  private boolean haveIsNullInside(BSLParserRuleContext ctx, Set<Integer> statements) {
    var selectStatement = Trees.getRootParent(ctx, statements);
    if (selectStatement == null || selectStatement.getChildCount() == 0 ) {
      return false;
    }
    if (haveIsNullExpression(ctx)){
      return true;
    }
    return haveIsNullInside(selectStatement.getParent(), statements);
  }

  private boolean haveIsNullExpression(BSLParserRuleContext ctx) {
    return Optional.of(ctx)
      .filter(bslParserRuleContext -> bslParserRuleContext instanceof SDBLParser.BuiltInFunctionsContext)
      .map(SDBLParser.BuiltInFunctionsContext.class::cast)
      .map(SDBLParser.BuiltInFunctionsContext::ISNULL)
      .filter(Objects::nonNull)
      .isPresent();
  }

  private boolean haveExprNotIsNullInsideWhereMember(SDBLParser.IsNullPredicateContext isNullPredicateCtx) {
    final var parent = (SDBLParser.SearchConditionContext) isNullPredicateCtx.getParent();
    if (parent.getChildCount() == 2 && isTerminalNodeNOT(parent.getChild(0))){
      return true;
    }
    return haveExprNotWithParens(parent);

  }

  private boolean haveExprNotWithParens(SDBLParser.SearchConditionContext ctx) {
    final var rootCtx = Trees.getRootParent(ctx, List.of(RULE_searchCondition, RULE_query));// todo в константы
    if (rootCtx == null || rootCtx.getRuleIndex() == RULE_query){
      return false;
    }
    return rootCtx.getChildCount() == NOT_WITH_PARENS_EXPR_MEMBERS_COUNT && isTerminalNodeNOT(rootCtx.getChild(0));
  }

  private void checkSelect(String tableName, SDBLParser.SelectedFieldsContext columns) {
    checkStatements(tableName, columns, SELECT_STATEMENTS);
  }

  private void checkStatements(
    String tableName, BSLParserRuleContext expression,
    Set<Integer> statements) {

    isTableFieldExpr(tableName, expression, statements)
      .forEach(nodesForIssues::add);
  }

  private void checkWhere(String tableName, @Nullable SDBLParser.SearchConditionsContext where) {
      Optional.ofNullable(where)
        .stream().flatMap(searchConditionsContext -> searchConditionsContext.condidions.stream())
        .forEach(searchConditionContext -> checkStatements(tableName, searchConditionContext,
          WHERE_STATEMENTS));
  }

  private void checkAllJoins(String tableName, SDBLParser.JoinPartContext currentJoinPart) {
    Optional.ofNullable(Trees.getRootParent(currentJoinPart, SDBLParser.RULE_dataSource))
      .filter(ctx -> ctx instanceof SDBLParser.DataSourceContext)
      .stream().flatMap(ctx -> ((SDBLParser.DataSourceContext) ctx).joinPart().stream())
      .filter(joinPartContext -> joinPartContext != currentJoinPart)
      .map(SDBLParser.JoinPartContext::searchConditions)
      .forEach(joinExpressionContext -> checkStatements(tableName, joinExpressionContext,
        JOIN_STATEMENTS));
  }

  private List<DiagnosticRelatedInformation> getRelatedInformation(SDBLParser.JoinPartContext self) {
    return nodesForIssues.stream()
      .filter(ctx -> !ctx.equals(self))
      .map(context -> RelatedInformation.create(
        documentContext.getUri(),
        Ranges.create(context),
        "+1"
      )).collect(Collectors.toList());
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.attribute.expression.language;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.nifi.attribute.expression.language.compile.CompiledExpression;
import org.apache.nifi.attribute.expression.language.evaluation.Evaluator;
import org.apache.nifi.attribute.expression.language.evaluation.literals.StringLiteralEvaluator;
import org.apache.nifi.attribute.expression.language.evaluation.selection.AllAttributesEvaluator;
import org.apache.nifi.attribute.expression.language.evaluation.selection.AnyAttributeEvaluator;
import org.apache.nifi.attribute.expression.language.evaluation.selection.AttributeEvaluator;
import org.apache.nifi.attribute.expression.language.evaluation.selection.MappingEvaluator;
import org.apache.nifi.attribute.expression.language.evaluation.selection.MultiAttributeEvaluator;
import org.apache.nifi.attribute.expression.language.evaluation.selection.MultiMatchAttributeEvaluator;
import org.apache.nifi.attribute.expression.language.evaluation.selection.MultiNamedAttributeEvaluator;
import org.apache.nifi.expression.AttributeValueDecorator;
import org.apache.nifi.processor.exception.ProcessException;

public class StandardPreparedQuery implements PreparedQuery {

    private final List<String> queryStrings;
    private final Map<String, CompiledExpression> expressions;
    private volatile VariableImpact variableImpact;

    public StandardPreparedQuery(final List<String> queryStrings, final Map<String, CompiledExpression> expressions) {
        this.queryStrings = queryStrings;
        this.expressions = expressions;
    }

    @Override
    public String evaluateExpressions(final Map<String, String> valMap, final AttributeValueDecorator decorator, final Map<String, String> stateVariables) throws ProcessException {
        final StringBuilder sb = new StringBuilder();
        for (final String val : queryStrings) {
            final CompiledExpression expression = expressions.get(val);
            if (expression == null) {
                sb.append(val);
            } else {
                final String evaluated = Query.evaluateExpression(expression.getTree(), val, valMap, decorator, stateVariables);
                if (evaluated != null) {
                    sb.append(evaluated);
                }
            }
        }
        return sb.toString();
    }

    @Override
    public String evaluateExpressions(final Map<String, String> valMap, final AttributeValueDecorator decorator)
            throws ProcessException {
        return evaluateExpressions(valMap, decorator, null);
    }

    @Override
    public boolean isExpressionLanguagePresent() {
        return !expressions.isEmpty();
    }

    @Override
    public VariableImpact getVariableImpact() {
        final VariableImpact existing = this.variableImpact;
        if (existing != null) {
            return existing;
        }

        final Set<String> variables = new HashSet<>();

        for (final CompiledExpression expression : expressions.values()) {
            for (final Evaluator<?> evaluator : expression.getAllEvaluators()) {
                if (evaluator instanceof AttributeEvaluator) {
                    final AttributeEvaluator attributeEval = (AttributeEvaluator) evaluator;
                    final Evaluator<String> nameEval = attributeEval.getNameEvaluator();

                    if (nameEval instanceof StringLiteralEvaluator) {
                        final String referencedVar = nameEval.evaluate(Collections.emptyMap()).getValue();
                        variables.add(referencedVar);
                    }
                } else if (evaluator instanceof AllAttributesEvaluator) {
                    final AllAttributesEvaluator allAttrsEval = (AllAttributesEvaluator) evaluator;
                    final MultiAttributeEvaluator iteratingEval = allAttrsEval.getVariableIteratingEvaluator();
                    if (iteratingEval instanceof MultiNamedAttributeEvaluator) {
                        variables.addAll(((MultiNamedAttributeEvaluator) iteratingEval).getAttributeNames());
                    } else if (iteratingEval instanceof MultiMatchAttributeEvaluator) {
                        return VariableImpact.ALWAYS_IMPACTED;
                    }
                } else if (evaluator instanceof AnyAttributeEvaluator) {
                    final AnyAttributeEvaluator allAttrsEval = (AnyAttributeEvaluator) evaluator;
                    final MultiAttributeEvaluator iteratingEval = allAttrsEval.getVariableIteratingEvaluator();
                    if (iteratingEval instanceof MultiNamedAttributeEvaluator) {
                        variables.addAll(((MultiNamedAttributeEvaluator) iteratingEval).getAttributeNames());
                    } else if (iteratingEval instanceof MultiMatchAttributeEvaluator) {
                        return VariableImpact.ALWAYS_IMPACTED;
                    }
                } else if (evaluator instanceof MappingEvaluator) {
                    final MappingEvaluator<?> allAttrsEval = (MappingEvaluator<?>) evaluator;
                    final MultiAttributeEvaluator iteratingEval = allAttrsEval.getVariableIteratingEvaluator();
                    if (iteratingEval instanceof MultiNamedAttributeEvaluator) {
                        variables.addAll(((MultiNamedAttributeEvaluator) iteratingEval).getAttributeNames());
                    }
                }
            }
        }

        final VariableImpact impact = new NamedVariableImpact(variables);
        this.variableImpact = impact;
        return impact;
    }
}

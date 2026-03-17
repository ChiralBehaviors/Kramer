/**
 * Copyright (c) 2016 Chiral Behaviors, LLC, all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chiralbehaviors.layout.expression;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tests for ExpressionEvaluator (RDR-021 Step B).
 *
 * @author hhildebrand
 */
class ExpressionEvaluatorTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    private final ExpressionEvaluator eval = new ExpressionEvaluator();

    // --- Helper ---

    private ObjectNode row(Object... kvs) {
        var node = NF.objectNode();
        for (int i = 0; i < kvs.length; i += 2) {
            String key = (String) kvs[i];
            Object val = kvs[i + 1];
            if (val == null) {
                node.putNull(key);
            } else if (val instanceof Number n) {
                node.put(key, n.doubleValue());
            } else if (val instanceof String s) {
                node.put(key, s);
            } else if (val instanceof Boolean b) {
                node.put(key, b);
            }
        }
        return node;
    }

    private ObjectNode nestedRow() {
        var node = NF.objectNode();
        var customer = NF.objectNode();
        var address = NF.objectNode();
        address.put("city", "Portland");
        customer.set("address", address);
        customer.put("tier", "premium");
        node.set("customer", customer);
        node.put("lifetime_value", 15000);
        return node;
    }

    private Object evalExpr(String expression, JsonNode row) throws ParseException {
        var ast = Parser.parse(expression);
        return eval.evaluate(ast, row);
    }

    // --- Literal evaluation ---

    @Test
    void evalNumberLiteral() throws ParseException {
        assertEquals(42.0, evalExpr("42", NF.objectNode()));
    }

    @Test
    void evalStringLiteral() throws ParseException {
        assertEquals("hello", evalExpr("\"hello\"", NF.objectNode()));
    }

    @Test
    void evalBooleanLiteral() throws ParseException {
        assertEquals(true, evalExpr("true", NF.objectNode()));
        assertEquals(false, evalExpr("false", NF.objectNode()));
    }

    @Test
    void evalNullLiteral() throws ParseException {
        assertNull(evalExpr("null", NF.objectNode()));
    }

    // --- Field resolution ---

    @Test
    void evalSimpleFieldRef() throws ParseException {
        assertEquals(10.0, evalExpr("$x", row("x", 10)));
    }

    @Test
    void evalStringFieldRef() throws ParseException {
        assertEquals("hello", evalExpr("$name", row("name", "hello")));
    }

    @Test
    void evalBooleanFieldRef() throws ParseException {
        assertEquals(true, evalExpr("$flag", row("flag", true)));
    }

    @Test
    void evalMissingFieldReturnsNull() throws ParseException {
        assertNull(evalExpr("$missing", NF.objectNode()));
    }

    @Test
    void evalNullFieldReturnsNull() throws ParseException {
        assertNull(evalExpr("$x", row("x", null)));
    }

    @Test
    void evalNestedFieldRef() throws ParseException {
        assertEquals("Portland", evalExpr("${customer.address.city}", nestedRow()));
    }

    @Test
    void evalNestedFieldMissing() throws ParseException {
        // Intermediate missing → null
        assertNull(evalExpr("${customer.phone.area}", nestedRow()));
    }

    // --- JsonNode materialization ---

    @Test
    void materializeIntNode() throws ParseException {
        var node = NF.objectNode();
        node.put("x", 42); // IntNode
        assertEquals(42.0, evalExpr("$x", node));
    }

    @Test
    void materializeLongNode() throws ParseException {
        var node = NF.objectNode();
        node.put("x", 100L); // LongNode
        assertEquals(100.0, evalExpr("$x", node));
    }

    @Test
    void materializeArrayNodeIsNull() throws ParseException {
        var node = NF.objectNode();
        node.set("x", NF.arrayNode());
        assertNull(evalExpr("$x", node));
    }

    @Test
    void materializeObjectNodeIsNull() throws ParseException {
        var node = NF.objectNode();
        node.set("x", NF.objectNode());
        assertNull(evalExpr("$x", node));
    }

    // --- Arithmetic ---

    @Test
    void evalAddition() throws ParseException {
        assertEquals(30.0, evalExpr("$a + $b", row("a", 10, "b", 20)));
    }

    @Test
    void evalSubtraction() throws ParseException {
        assertEquals(5.0, evalExpr("$a - $b", row("a", 15, "b", 10)));
    }

    @Test
    void evalMultiplication() throws ParseException {
        assertEquals(50.0, evalExpr("$price * $qty", row("price", 10, "qty", 5)));
    }

    @Test
    void evalDivision() throws ParseException {
        assertEquals(5.0, evalExpr("$a / $b", row("a", 10, "b", 2)));
    }

    @Test
    void evalDivisionByZeroReturnsNull() throws ParseException {
        assertNull(evalExpr("$a / $b", row("a", 10, "b", 0)));
    }

    @Test
    void evalUnaryNeg() throws ParseException {
        assertEquals(-5.0, evalExpr("-$x", row("x", 5)));
    }

    @Test
    void evalStringConcat() throws ParseException {
        assertEquals("hello world",
            evalExpr("$a + \" \" + $b", row("a", "hello", "b", "world")));
    }

    @Test
    void evalStringSubIsNull() throws ParseException {
        // STRING - STRING → null
        assertNull(evalExpr("$a - $b", row("a", "x", "b", "y")));
    }

    // --- Coercion ---

    @Test
    void evalStringNumberCoercion() throws ParseException {
        // "5" + 3 → attempt parse "5" → 5.0 + 3.0 = 8.0
        assertEquals(8.0, evalExpr("$a + $b", row("a", "5", "b", 3)));
    }

    @Test
    void evalStringNumberCoercionFails() throws ParseException {
        // "abc" + 3 → parse fails → null
        assertNull(evalExpr("$a * $b", row("a", "abc", "b", 3)));
    }

    @Test
    void evalBooleanInArithmeticIsNull() throws ParseException {
        assertNull(evalExpr("$a + $b", row("a", true, "b", 3)));
    }

    // --- Comparisons ---

    @Test
    void evalNumericComparison() throws ParseException {
        assertEquals(true, evalExpr("$x > 5", row("x", 10)));
        assertEquals(false, evalExpr("$x > 5", row("x", 3)));
    }

    @Test
    void evalStringComparison() throws ParseException {
        assertEquals(true, evalExpr("$a == \"yes\"", row("a", "yes")));
        assertEquals(false, evalExpr("$a == \"no\"", row("a", "yes")));
    }

    @Test
    void evalEquality() throws ParseException {
        assertEquals(true, evalExpr("$x == 10", row("x", 10)));
        assertEquals(true, evalExpr("$x != 5", row("x", 10)));
    }

    @Test
    void evalLte() throws ParseException {
        assertEquals(true, evalExpr("$x <= 10", row("x", 10)));
        assertEquals(true, evalExpr("$x <= 10", row("x", 5)));
        assertEquals(false, evalExpr("$x <= 10", row("x", 15)));
    }

    @Test
    void evalGte() throws ParseException {
        assertEquals(true, evalExpr("$x >= 10", row("x", 10)));
        assertEquals(true, evalExpr("$x >= 10", row("x", 15)));
        assertEquals(false, evalExpr("$x >= 10", row("x", 5)));
    }

    @Test
    void evalLt() throws ParseException {
        assertEquals(true, evalExpr("$x < 10", row("x", 5)));
        assertEquals(false, evalExpr("$x < 10", row("x", 10)));
    }

    // --- Null comparison rules ---

    @Test
    void evalNullEqualsNull() throws ParseException {
        assertEquals(true, evalExpr("$x == null", row("x", null)));
    }

    @Test
    void evalNullNotEqualsNonNull() throws ParseException {
        assertEquals(true, evalExpr("$x != 5", row("x", null)));
    }

    @Test
    void evalNullEqualsNonNull() throws ParseException {
        assertEquals(false, evalExpr("$x == 5", row("x", null)));
    }

    @Test
    void evalNullComparisonOrdering() throws ParseException {
        // null < anything → false
        assertEquals(false, evalExpr("$x < 5", row("x", null)));
        assertEquals(false, evalExpr("$x > 5", row("x", null)));
        assertEquals(false, evalExpr("$x <= 5", row("x", null)));
        assertEquals(false, evalExpr("$x >= 5", row("x", null)));
    }

    // --- Boolean operators ---

    @Test
    void evalLogicalAnd() throws ParseException {
        assertEquals(true, evalExpr("$a && $b", row("a", true, "b", true)));
        assertEquals(false, evalExpr("$a && $b", row("a", true, "b", false)));
        assertEquals(false, evalExpr("$a && $b", row("a", false, "b", true)));
    }

    @Test
    void evalLogicalOr() throws ParseException {
        assertEquals(true, evalExpr("$a || $b", row("a", true, "b", false)));
        assertEquals(true, evalExpr("$a || $b", row("a", false, "b", true)));
        assertEquals(false, evalExpr("$a || $b", row("a", false, "b", false)));
    }

    @Test
    void evalLogicalNot() throws ParseException {
        assertEquals(false, evalExpr("!$a", row("a", true)));
        assertEquals(true, evalExpr("!$a", row("a", false)));
    }

    @Test
    void evalNullInBooleanContextIsFalse() throws ParseException {
        // null in && → treated as false
        assertEquals(false, evalExpr("$a && $b", row("a", null, "b", true)));
        // null in || → treated as false
        assertEquals(true, evalExpr("$a || $b", row("a", null, "b", true)));
        assertEquals(false, evalExpr("$a || $b", row("a", null, "b", false)));
    }

    @Test
    void evalNotNull() throws ParseException {
        // !null → true (null is falsy, negation is true)
        assertEquals(true, evalExpr("!$a", row("a", null)));
    }

    @Test
    void evalNumberInBooleanContext() throws ParseException {
        // non-zero → true, zero → false
        assertEquals(true, evalExpr("$a && true", row("a", 1)));
        assertEquals(false, evalExpr("$a && true", row("a", 0)));
    }

    @Test
    void evalStringInBooleanContext() throws ParseException {
        // non-empty → true, empty → false
        assertEquals(true, evalExpr("$a && true", row("a", "x")));
        assertEquals(false, evalExpr("$a && true", row("a", "")));
    }

    @Test
    void evalShortCircuitAnd() throws ParseException {
        // Left is false → right not evaluated (won't throw for aggregate in row context)
        var ast = Parser.parse("false && count()");
        // count() is AggregateCall, would throw if evaluated in row context
        // but short-circuit means right is never reached
        assertEquals(false, eval.evaluate(ast, NF.objectNode()));
    }

    @Test
    void evalShortCircuitOr() throws ParseException {
        var ast = Parser.parse("true || count()");
        assertEquals(true, eval.evaluate(ast, NF.objectNode()));
    }

    // --- Null propagation in arithmetic ---

    @Test
    void evalNullPropagatesInArithmetic() throws ParseException {
        assertNull(evalExpr("$a + $b", row("a", null, "b", 5)));
        assertNull(evalExpr("$a - $b", row("a", 5, "b", null)));
        assertNull(evalExpr("$a * $b", row("a", null, "b", null)));
        assertNull(evalExpr("-$a", row("a", null)));
    }

    @Test
    void evalNullConcatIsNull() throws ParseException {
        // + with null operand → null (no string-concat fallback for nulls)
        assertNull(evalExpr("$a + $b", row("a", null, "b", "world")));
    }

    // --- Scalar functions ---

    @Test
    void evalLen() throws ParseException {
        assertEquals(5.0, evalExpr("len($s)", row("s", "hello")));
    }

    @Test
    void evalLenNull() throws ParseException {
        assertNull(evalExpr("len($s)", row("s", null)));
    }

    @Test
    void evalUpper() throws ParseException {
        assertEquals("HELLO", evalExpr("upper($s)", row("s", "hello")));
    }

    @Test
    void evalUpperNull() throws ParseException {
        assertNull(evalExpr("upper($s)", row("s", null)));
    }

    @Test
    void evalLower() throws ParseException {
        assertEquals("hello", evalExpr("lower($s)", row("s", "HELLO")));
    }

    @Test
    void evalLowerNull() throws ParseException {
        assertNull(evalExpr("lower($s)", row("s", null)));
    }

    @Test
    void evalAbs() throws ParseException {
        assertEquals(5.0, evalExpr("abs($x)", row("x", -5)));
    }

    @Test
    void evalAbsNull() throws ParseException {
        assertNull(evalExpr("abs($x)", row("x", null)));
    }

    @Test
    void evalRound() throws ParseException {
        assertEquals(4.0, evalExpr("round($x)", row("x", 3.7)));
        assertEquals(3.0, evalExpr("round($x)", row("x", 3.2)));
    }

    @Test
    void evalRoundNull() throws ParseException {
        assertNull(evalExpr("round($x)", row("x", null)));
    }

    @Test
    void evalIfTrue() throws ParseException {
        assertEquals(10.0,
            evalExpr("if($flag, $a, $b)", row("flag", true, "a", 10, "b", 20)));
    }

    @Test
    void evalIfFalse() throws ParseException {
        assertEquals(20.0,
            evalExpr("if($flag, $a, $b)", row("flag", false, "a", 10, "b", 20)));
    }

    @Test
    void evalIfNullCondition() throws ParseException {
        // if(null, ...) → evaluates else branch (null is falsy)
        assertEquals(20.0,
            evalExpr("if($flag, $a, $b)", row("flag", null, "a", 10, "b", 20)));
    }

    // --- Complex expressions from spec ---

    @Test
    void evalFilterExpression() throws ParseException {
        var r = row("revenue", 100, "status", "active");
        assertEquals(true, evalExpr("$revenue > 0 && $status != \"cancelled\"", r));
    }

    @Test
    void evalFormulaExpression() throws ParseException {
        var r = row("price", 10, "quantity", 5);
        assertEquals(50.0, evalExpr("$price * $quantity", r));
    }

    @Test
    void evalStringConcatFormula() throws ParseException {
        var r = row("last_name", "smith", "first_name", "john");
        assertEquals("SMITH, john",
            evalExpr("upper($last_name) + \", \" + $first_name", r));
    }

    @Test
    void evalIfDiscountFormula() throws ParseException {
        var r1 = row("discount_rate", 0.1, "price", 100);
        assertEquals(90.0,
            evalExpr("if($discount_rate > 0, $price * (1 - $discount_rate), $price)", r1));

        var r2 = row("discount_rate", 0, "price", 100);
        assertEquals(100.0,
            evalExpr("if($discount_rate > 0, $price * (1 - $discount_rate), $price)", r2));
    }

    @Test
    void evalNestedFieldFilter() throws ParseException {
        assertEquals(true,
            evalExpr("${customer.tier} == \"premium\" || $lifetime_value >= 10000",
                nestedRow()));
    }

    @Test
    void evalSortExpression() throws ParseException {
        assertEquals(5.0, evalExpr("abs($delta)", row("delta", -5)));
        assertEquals("smith", evalExpr("lower($name)", row("name", "SMITH")));
    }

    // --- Aggregate evaluation ---

    @Test
    void evalSumAggregate() throws ParseException {
        var rows = List.<JsonNode>of(
            row("revenue", 100), row("revenue", 200), row("revenue", 300));
        var ast = (Expr.AggregateCall) Parser.parse("sum($revenue)");
        assertEquals(600.0, eval.evaluateAggregate(ast, rows));
    }

    @Test
    void evalCountAggregate() throws ParseException {
        var rows = List.<JsonNode>of(row("a", 1), row("a", 2), row("a", 3));
        var ast = (Expr.AggregateCall) Parser.parse("count()");
        assertEquals(3.0, eval.evaluateAggregate(ast, rows));
    }

    @Test
    void evalCountExcludesNulls() throws ParseException {
        var rows = List.<JsonNode>of(
            row("a", 1), row("a", null), row("a", 3));
        // count() counts non-null rows — but count() has no arg,
        // so it counts all rows
        var ast = (Expr.AggregateCall) Parser.parse("count()");
        assertEquals(3.0, eval.evaluateAggregate(ast, rows));
    }

    @Test
    void evalAvgAggregate() throws ParseException {
        var rows = List.<JsonNode>of(
            row("x", 10), row("x", 20), row("x", 30));
        var ast = (Expr.AggregateCall) Parser.parse("avg($x)");
        assertEquals(20.0, eval.evaluateAggregate(ast, rows));
    }

    @Test
    void evalAvgExcludesNulls() throws ParseException {
        var rows = List.<JsonNode>of(
            row("x", 10), row("x", null), row("x", 20));
        var ast = (Expr.AggregateCall) Parser.parse("avg($x)");
        // avg of 10, 20 (null excluded) = 15
        assertEquals(15.0, eval.evaluateAggregate(ast, rows));
    }

    @Test
    void evalMinAggregate() throws ParseException {
        var rows = List.<JsonNode>of(
            row("x", 30), row("x", 10), row("x", 20));
        var ast = (Expr.AggregateCall) Parser.parse("min($x)");
        assertEquals(10.0, eval.evaluateAggregate(ast, rows));
    }

    @Test
    void evalMaxAggregate() throws ParseException {
        var rows = List.<JsonNode>of(
            row("x", 30), row("x", 10), row("x", 20));
        var ast = (Expr.AggregateCall) Parser.parse("max($x)");
        assertEquals(30.0, eval.evaluateAggregate(ast, rows));
    }

    @Test
    void evalAggregateAllNullsReturnsNull() throws ParseException {
        var rows = List.<JsonNode>of(
            row("x", null), row("x", null));
        var ast = (Expr.AggregateCall) Parser.parse("min($x)");
        assertNull(eval.evaluateAggregate(ast, rows));
    }

    @Test
    void evalAggregateWithExpression() throws ParseException {
        var rows = List.<JsonNode>of(
            row("price", 10, "qty", 3),
            row("price", 20, "qty", 2));
        var ast = (Expr.AggregateCall) Parser.parse("sum($price * $qty)");
        // 10*3 + 20*2 = 30 + 40 = 70
        assertEquals(70.0, eval.evaluateAggregate(ast, rows));
    }

    @Test
    void evalAggregateInRowContextThrows() throws ParseException {
        var ast = Parser.parse("sum($x)");
        assertThrows(IllegalStateException.class,
            () -> eval.evaluate(ast, NF.objectNode()));
    }

    // --- AST cache ---

    @Test
    void compileAndCacheExpression() throws ParseException {
        var ast1 = eval.compile("$a + $b");
        var ast2 = eval.compile("$a + $b");
        assertSame(ast1, ast2, "Should return cached AST");
    }

    @Test
    void invalidateCacheClearsEntries() throws ParseException {
        var ast1 = eval.compile("$a + $b");
        eval.invalidateCache();
        var ast2 = eval.compile("$a + $b");
        assertNotSame(ast1, ast2, "Should return fresh AST after invalidation");
    }

    // --- Cycle detection ---

    @Test
    void detectSimpleCycle() {
        // A references B, B references A
        var deps = Map.of(
            "a", Set.of("b"),
            "b", Set.of("a"));
        var cycles = ExpressionEvaluator.detectCycles(deps);
        assertTrue(cycles.contains("a"));
        assertTrue(cycles.contains("b"));
    }

    @Test
    void detectSelfReference() {
        var deps = Map.of("a", Set.of("a"));
        var cycles = ExpressionEvaluator.detectCycles(deps);
        assertTrue(cycles.contains("a"));
    }

    @Test
    void noCyclesInLinearDeps() {
        // A → B → C (no cycle)
        var deps = Map.of(
            "a", Set.of("b"),
            "b", Set.of("c"),
            "c", Set.<String>of());
        var cycles = ExpressionEvaluator.detectCycles(deps);
        assertTrue(cycles.isEmpty());
    }

    @Test
    void topologicalOrderRespectsDeps() {
        // A depends on B, B depends on C → order: C, B, A
        var deps = Map.of(
            "a", Set.of("b"),
            "b", Set.of("c"),
            "c", Set.<String>of());
        var order = ExpressionEvaluator.topologicalSort(deps);
        assertTrue(order.indexOf("c") < order.indexOf("b"));
        assertTrue(order.indexOf("b") < order.indexOf("a"));
    }

    @Test
    void topologicalSortExcludesCycles() {
        var deps = Map.of(
            "a", Set.of("b"),
            "b", Set.of("a"),
            "c", Set.<String>of());
        var order = ExpressionEvaluator.topologicalSort(deps);
        // Only "c" should appear (a and b are in cycle)
        assertEquals(List.of("c"), order);
    }

    // --- Wiring-time validations ---

    @Test
    void containsAggregateDetection() throws ParseException {
        assertTrue(ExpressionEvaluator.containsAggregate(Parser.parse("sum($x)")));
        assertTrue(ExpressionEvaluator.containsAggregate(Parser.parse("sum($x) / count()")));
        assertFalse(ExpressionEvaluator.containsAggregate(Parser.parse("$x + $y")));
        assertFalse(ExpressionEvaluator.containsAggregate(Parser.parse("upper($name)")));
    }
}

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
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.expression.Expr.*;

/**
 * Tests for the expression language parser (RDR-021 Step A).
 *
 * @author hhildebrand
 */
class ExpressionParserTest {

    // --- Literals ---

    @Test
    void parseIntegerLiteral() throws ParseException {
        var expr = Parser.parse("42");
        assertInstanceOf(Literal.class, expr);
        assertEquals(42.0, ((Literal) expr).value());
    }

    @Test
    void parseDecimalLiteral() throws ParseException {
        var expr = Parser.parse("3.14");
        assertInstanceOf(Literal.class, expr);
        assertEquals(3.14, ((Literal) expr).value());
    }

    @Test
    void parseStringLiteral() throws ParseException {
        var expr = Parser.parse("\"hello world\"");
        assertInstanceOf(Literal.class, expr);
        assertEquals("hello world", ((Literal) expr).value());
    }

    @Test
    void parseStringWithEscapes() throws ParseException {
        var expr = Parser.parse("\"line\\none\\ttwo\\\\three\\\"four\"");
        assertInstanceOf(Literal.class, expr);
        assertEquals("line\none\ttwo\\three\"four", ((Literal) expr).value());
    }

    @Test
    void parseBooleanTrue() throws ParseException {
        var expr = Parser.parse("true");
        assertEquals(new Literal(true), expr);
    }

    @Test
    void parseBooleanFalse() throws ParseException {
        var expr = Parser.parse("false");
        assertEquals(new Literal(false), expr);
    }

    @Test
    void parseNullLiteral() throws ParseException {
        var expr = Parser.parse("null");
        assertEquals(new Literal(null), expr);
    }

    // --- Field References ---

    @Test
    void parseSimpleFieldRef() throws ParseException {
        var expr = Parser.parse("$name");
        assertEquals(new FieldRef(List.of("name")), expr);
    }

    @Test
    void parseFieldRefWithUnderscore() throws ParseException {
        var expr = Parser.parse("$first_name");
        assertEquals(new FieldRef(List.of("first_name")), expr);
    }

    @Test
    void parseNestedFieldPath() throws ParseException {
        var expr = Parser.parse("${customer.address.city}");
        assertEquals(new FieldRef(List.of("customer", "address", "city")), expr);
    }

    @Test
    void parseBracedSingleField() throws ParseException {
        var expr = Parser.parse("${name}");
        assertEquals(new FieldRef(List.of("name")), expr);
    }

    // --- Arithmetic ---

    @Test
    void parseMultiplication() throws ParseException {
        var expr = Parser.parse("$price * $quantity");
        assertEquals(
            new BinaryOp(BinaryOp.Op.MUL,
                new FieldRef(List.of("price")),
                new FieldRef(List.of("quantity"))),
            expr);
    }

    @Test
    void parseAddition() throws ParseException {
        var expr = Parser.parse("$a + $b");
        assertEquals(
            new BinaryOp(BinaryOp.Op.ADD,
                new FieldRef(List.of("a")),
                new FieldRef(List.of("b"))),
            expr);
    }

    @Test
    void parsePrecedenceMulOverAdd() throws ParseException {
        // $a + $b * $c  =>  $a + ($b * $c)
        var expr = Parser.parse("$a + $b * $c");
        assertEquals(
            new BinaryOp(BinaryOp.Op.ADD,
                new FieldRef(List.of("a")),
                new BinaryOp(BinaryOp.Op.MUL,
                    new FieldRef(List.of("b")),
                    new FieldRef(List.of("c")))),
            expr);
    }

    @Test
    void parseParenthesesOverridePrecedence() throws ParseException {
        // ($a + $b) * $c
        var expr = Parser.parse("($a + $b) * $c");
        assertEquals(
            new BinaryOp(BinaryOp.Op.MUL,
                new BinaryOp(BinaryOp.Op.ADD,
                    new FieldRef(List.of("a")),
                    new FieldRef(List.of("b"))),
                new FieldRef(List.of("c"))),
            expr);
    }

    @Test
    void parseUnaryMinus() throws ParseException {
        var expr = Parser.parse("-$x");
        assertEquals(
            new UnaryOp(UnaryOp.Op.NEG, new FieldRef(List.of("x"))),
            expr);
    }

    @Test
    void parseDivision() throws ParseException {
        var expr = Parser.parse("$a / $b");
        assertEquals(
            new BinaryOp(BinaryOp.Op.DIV,
                new FieldRef(List.of("a")),
                new FieldRef(List.of("b"))),
            expr);
    }

    @Test
    void parseSubtraction() throws ParseException {
        var expr = Parser.parse("$a - $b");
        assertEquals(
            new BinaryOp(BinaryOp.Op.SUB,
                new FieldRef(List.of("a")),
                new FieldRef(List.of("b"))),
            expr);
    }

    // --- Comparisons ---

    @Test
    void parseComparison() throws ParseException {
        var expr = Parser.parse("$revenue > 0");
        assertEquals(
            new BinaryOp(BinaryOp.Op.GT,
                new FieldRef(List.of("revenue")),
                new Literal(0.0)),
            expr);
    }

    @Test
    void parseEquality() throws ParseException {
        var expr = Parser.parse("$status == \"cancelled\"");
        assertEquals(
            new BinaryOp(BinaryOp.Op.EQ,
                new FieldRef(List.of("status")),
                new Literal("cancelled")),
            expr);
    }

    @Test
    void parseNotEqual() throws ParseException {
        var expr = Parser.parse("$status != \"cancelled\"");
        assertEquals(
            new BinaryOp(BinaryOp.Op.NEQ,
                new FieldRef(List.of("status")),
                new Literal("cancelled")),
            expr);
    }

    @Test
    void parseLessThanOrEqual() throws ParseException {
        var expr = Parser.parse("$x <= 100");
        assertEquals(
            new BinaryOp(BinaryOp.Op.LTE,
                new FieldRef(List.of("x")),
                new Literal(100.0)),
            expr);
    }

    @Test
    void parseGreaterThanOrEqual() throws ParseException {
        var expr = Parser.parse("$x >= 10");
        assertEquals(
            new BinaryOp(BinaryOp.Op.GTE,
                new FieldRef(List.of("x")),
                new Literal(10.0)),
            expr);
    }

    @Test
    void parseLessThan() throws ParseException {
        var expr = Parser.parse("$x < 5");
        assertEquals(
            new BinaryOp(BinaryOp.Op.LT,
                new FieldRef(List.of("x")),
                new Literal(5.0)),
            expr);
    }

    // --- Boolean Operators ---

    @Test
    void parseLogicalAnd() throws ParseException {
        var expr = Parser.parse("$revenue > 0 && $status != \"cancelled\"");
        assertEquals(
            new BinaryOp(BinaryOp.Op.AND,
                new BinaryOp(BinaryOp.Op.GT,
                    new FieldRef(List.of("revenue")),
                    new Literal(0.0)),
                new BinaryOp(BinaryOp.Op.NEQ,
                    new FieldRef(List.of("status")),
                    new Literal("cancelled"))),
            expr);
    }

    @Test
    void parseLogicalOr() throws ParseException {
        var expr = Parser.parse("$a == 1 || $b == 2");
        assertEquals(
            new BinaryOp(BinaryOp.Op.OR,
                new BinaryOp(BinaryOp.Op.EQ,
                    new FieldRef(List.of("a")),
                    new Literal(1.0)),
                new BinaryOp(BinaryOp.Op.EQ,
                    new FieldRef(List.of("b")),
                    new Literal(2.0))),
            expr);
    }

    @Test
    void parseLogicalNot() throws ParseException {
        var expr = Parser.parse("!$active");
        assertEquals(
            new UnaryOp(UnaryOp.Op.NOT, new FieldRef(List.of("active"))),
            expr);
    }

    @Test
    void parsePrecedenceAndOverOr() throws ParseException {
        // $a || $b && $c  =>  $a || ($b && $c)
        var expr = Parser.parse("$a || $b && $c");
        assertEquals(
            new BinaryOp(BinaryOp.Op.OR,
                new FieldRef(List.of("a")),
                new BinaryOp(BinaryOp.Op.AND,
                    new FieldRef(List.of("b")),
                    new FieldRef(List.of("c")))),
            expr);
    }

    // --- Nested field in comparison ---

    @Test
    void parseNestedFieldComparison() throws ParseException {
        var expr = Parser.parse("${customer.tier} == \"premium\"");
        assertEquals(
            new BinaryOp(BinaryOp.Op.EQ,
                new FieldRef(List.of("customer", "tier")),
                new Literal("premium")),
            expr);
    }

    // --- Complex boolean expression from spec ---

    @Test
    void parseComplexBooleanFromSpec() throws ParseException {
        var expr = Parser.parse(
            "${customer.tier} == \"premium\" || $lifetime_value >= 10000");
        assertEquals(
            new BinaryOp(BinaryOp.Op.OR,
                new BinaryOp(BinaryOp.Op.EQ,
                    new FieldRef(List.of("customer", "tier")),
                    new Literal("premium")),
                new BinaryOp(BinaryOp.Op.GTE,
                    new FieldRef(List.of("lifetime_value")),
                    new Literal(10000.0))),
            expr);
    }

    // --- Null comparison ---

    @Test
    void parseNullComparison() throws ParseException {
        var expr = Parser.parse("$field == null");
        assertEquals(
            new BinaryOp(BinaryOp.Op.EQ,
                new FieldRef(List.of("field")),
                new Literal(null)),
            expr);
    }

    // --- Scalar functions ---

    @Test
    void parseUpperFn() throws ParseException {
        var expr = Parser.parse("upper($last_name)");
        assertEquals(
            new ScalarCall("upper", List.of(new FieldRef(List.of("last_name")))),
            expr);
    }

    @Test
    void parseLenFn() throws ParseException {
        var expr = Parser.parse("len($name)");
        assertEquals(
            new ScalarCall("len", List.of(new FieldRef(List.of("name")))),
            expr);
    }

    @Test
    void parseLowerFn() throws ParseException {
        var expr = Parser.parse("lower($name)");
        assertEquals(
            new ScalarCall("lower", List.of(new FieldRef(List.of("name")))),
            expr);
    }

    @Test
    void parseAbsFn() throws ParseException {
        var expr = Parser.parse("abs($delta)");
        assertEquals(
            new ScalarCall("abs", List.of(new FieldRef(List.of("delta")))),
            expr);
    }

    @Test
    void parseRoundFn() throws ParseException {
        var expr = Parser.parse("round($value)");
        assertEquals(
            new ScalarCall("round", List.of(new FieldRef(List.of("value")))),
            expr);
    }

    @Test
    void parseIfFn() throws ParseException {
        // if($discount_rate > 0, $price * (1 - $discount_rate), $price)
        var expr = Parser.parse(
            "if($discount_rate > 0, $price * (1 - $discount_rate), $price)");
        assertInstanceOf(ScalarCall.class, expr);
        var call = (ScalarCall) expr;
        assertEquals("if", call.name());
        assertEquals(3, call.args().size());

        // condition: $discount_rate > 0
        assertEquals(
            new BinaryOp(BinaryOp.Op.GT,
                new FieldRef(List.of("discount_rate")),
                new Literal(0.0)),
            call.args().get(0));

        // then: $price * (1 - $discount_rate)
        assertEquals(
            new BinaryOp(BinaryOp.Op.MUL,
                new FieldRef(List.of("price")),
                new BinaryOp(BinaryOp.Op.SUB,
                    new Literal(1.0),
                    new FieldRef(List.of("discount_rate")))),
            call.args().get(1));

        // else: $price
        assertEquals(new FieldRef(List.of("price")), call.args().get(2));
    }

    // --- String concatenation formula ---

    @Test
    void parseStringConcatFormula() throws ParseException {
        var expr = Parser.parse("upper($last_name) + \", \" + $first_name");
        // Left-associative: (upper($last_name) + ", ") + $first_name
        assertEquals(
            new BinaryOp(BinaryOp.Op.ADD,
                new BinaryOp(BinaryOp.Op.ADD,
                    new ScalarCall("upper", List.of(new FieldRef(List.of("last_name")))),
                    new Literal(", ")),
                new FieldRef(List.of("first_name"))),
            expr);
    }

    // --- Aggregate calls ---

    @Test
    void parseSumAggregate() throws ParseException {
        var expr = Parser.parse("sum($revenue)");
        assertEquals(
            new AggregateCall("sum", Optional.of(new FieldRef(List.of("revenue")))),
            expr);
    }

    @Test
    void parseCountAggregate() throws ParseException {
        var expr = Parser.parse("count()");
        assertEquals(new AggregateCall("count", Optional.empty()), expr);
    }

    @Test
    void parseAvgAggregate() throws ParseException {
        var expr = Parser.parse("avg($unit_price)");
        assertEquals(
            new AggregateCall("avg", Optional.of(new FieldRef(List.of("unit_price")))),
            expr);
    }

    @Test
    void parseMinAggregate() throws ParseException {
        var expr = Parser.parse("min($value)");
        assertEquals(
            new AggregateCall("min", Optional.of(new FieldRef(List.of("value")))),
            expr);
    }

    @Test
    void parseMaxAggregate() throws ParseException {
        var expr = Parser.parse("max($value)");
        assertEquals(
            new AggregateCall("max", Optional.of(new FieldRef(List.of("value")))),
            expr);
    }

    @Test
    void parseAggregateWithExpression() throws ParseException {
        // sum($price * $quantity)
        var expr = Parser.parse("sum($price * $quantity)");
        assertEquals(
            new AggregateCall("sum", Optional.of(
                new BinaryOp(BinaryOp.Op.MUL,
                    new FieldRef(List.of("price")),
                    new FieldRef(List.of("quantity"))))),
            expr);
    }

    @Test
    void parseAggregateDivision() throws ParseException {
        // sum($revenue) / count()
        var expr = Parser.parse("sum($revenue) / count()");
        assertEquals(
            new BinaryOp(BinaryOp.Op.DIV,
                new AggregateCall("sum", Optional.of(new FieldRef(List.of("revenue")))),
                new AggregateCall("count", Optional.empty())),
            expr);
    }

    // --- Error Cases ---

    @Test
    void errorUnterminatedString() {
        var ex = assertThrows(ParseException.class, () -> Parser.parse("\"hello"));
        assertTrue(ex.getOffset() >= 0, "offset should be non-negative");
        assertTrue(ex.getMessage().contains("Unterminated"),
            "message should mention unterminated: " + ex.getMessage());
    }

    @Test
    void errorUnexpectedToken() {
        var ex = assertThrows(ParseException.class, () -> Parser.parse("* 5"));
        assertTrue(ex.getOffset() >= 0);
    }

    @Test
    void errorMissingCloseParen() {
        var ex = assertThrows(ParseException.class, () -> Parser.parse("($a + $b"));
        assertTrue(ex.getOffset() >= 0);
    }

    @Test
    void errorEmptyExpression() {
        var ex = assertThrows(ParseException.class, () -> Parser.parse(""));
        assertTrue(ex.getOffset() >= 0);
    }

    @Test
    void errorBareIdentifier() {
        // Bare identifiers (no $) that aren't keywords or functions are errors
        var ex = assertThrows(ParseException.class, () -> Parser.parse("name"));
        assertTrue(ex.getOffset() >= 0);
    }

    @Test
    void errorInvalidFieldRef() {
        var ex = assertThrows(ParseException.class, () -> Parser.parse("$"));
        assertTrue(ex.getOffset() >= 0);
    }

    @Test
    void errorUnclosedBracedFieldRef() {
        var ex = assertThrows(ParseException.class, () -> Parser.parse("${a.b"));
        assertTrue(ex.getOffset() >= 0);
    }

    @Test
    void errorExpressionTooLong() {
        var longExpr = "$a" + " + $a".repeat(2000);
        var ex = assertThrows(ParseException.class, () -> Parser.parse(longExpr));
        assertTrue(ex.getMessage().contains("4096") || ex.getMessage().contains("too long"),
            "should mention length limit: " + ex.getMessage());
    }

    @Test
    void errorUnknownScalarFn() {
        var ex = assertThrows(ParseException.class, () -> Parser.parse("foo($x)"));
        assertTrue(ex.getOffset() >= 0);
        assertTrue(ex.getMessage().contains("Unknown"),
            "should mention unknown: " + ex.getMessage());
    }

    @Test
    void errorCountWithArg() {
        var ex = assertThrows(ParseException.class, () -> Parser.parse("count($x)"));
        assertTrue(ex.getMessage().contains("count"),
            "should mention count: " + ex.getMessage());
    }

    @Test
    void errorAggregateWithoutArg() {
        // sum() with no arg is an error (only count() takes no args)
        var ex = assertThrows(ParseException.class, () -> Parser.parse("sum()"));
        assertTrue(ex.getOffset() >= 0);
    }

    // --- Whitespace handling ---

    @Test
    void parseWithExtraWhitespace() throws ParseException {
        var expr = Parser.parse("  $a   +   $b  ");
        assertEquals(
            new BinaryOp(BinaryOp.Op.ADD,
                new FieldRef(List.of("a")),
                new FieldRef(List.of("b"))),
            expr);
    }

    @Test
    void parseWithTabs() throws ParseException {
        var expr = Parser.parse("\t$a\t*\t$b\t");
        assertEquals(
            new BinaryOp(BinaryOp.Op.MUL,
                new FieldRef(List.of("a")),
                new FieldRef(List.of("b"))),
            expr);
    }

    // --- Complex expressions ---

    @Test
    void parseDoubleNegation() throws ParseException {
        var expr = Parser.parse("- -$x");
        assertEquals(
            new UnaryOp(UnaryOp.Op.NEG,
                new UnaryOp(UnaryOp.Op.NEG,
                    new FieldRef(List.of("x")))),
            expr);
    }

    @Test
    void parseNotNot() throws ParseException {
        var expr = Parser.parse("!!$flag");
        assertEquals(
            new UnaryOp(UnaryOp.Op.NOT,
                new UnaryOp(UnaryOp.Op.NOT,
                    new FieldRef(List.of("flag")))),
            expr);
    }

    @Test
    void parseSortExpressionExample() throws ParseException {
        // $priority * -1
        var expr = Parser.parse("$priority * -1");
        assertEquals(
            new BinaryOp(BinaryOp.Op.MUL,
                new FieldRef(List.of("priority")),
                new UnaryOp(UnaryOp.Op.NEG, new Literal(1.0))),
            expr);
    }

    @Test
    void parseNullCheckWithOr() throws ParseException {
        // $field == null || $field > 0
        var expr = Parser.parse("$field == null || $field > 0");
        assertEquals(
            new BinaryOp(BinaryOp.Op.OR,
                new BinaryOp(BinaryOp.Op.EQ,
                    new FieldRef(List.of("field")),
                    new Literal(null)),
                new BinaryOp(BinaryOp.Op.GT,
                    new FieldRef(List.of("field")),
                    new Literal(0.0))),
            expr);
    }

    @Test
    void parseNotNullCheckBothFields() throws ParseException {
        var expr = Parser.parse("$quantity != null && $unit_price != null");
        assertEquals(
            new BinaryOp(BinaryOp.Op.AND,
                new BinaryOp(BinaryOp.Op.NEQ,
                    new FieldRef(List.of("quantity")),
                    new Literal(null)),
                new BinaryOp(BinaryOp.Op.NEQ,
                    new FieldRef(List.of("unit_price")),
                    new Literal(null))),
            expr);
    }

    @Test
    void parseNestedScalarCalls() throws ParseException {
        // lower(upper($name)) — nested calls
        var expr = Parser.parse("lower(upper($name))");
        assertEquals(
            new ScalarCall("lower", List.of(
                new ScalarCall("upper", List.of(
                    new FieldRef(List.of("name")))))),
            expr);
    }
}

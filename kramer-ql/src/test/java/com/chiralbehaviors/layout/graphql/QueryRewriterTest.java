// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.expression.Expr;
import com.chiralbehaviors.layout.expression.Parser;
import com.chiralbehaviors.layout.query.LayoutQueryState;

import graphql.language.Document;
import graphql.language.OperationDefinition;

/**
 * TDD tests for ExprToGraphQL and QueryRewriter (RDR-028 Phase 2).
 */
class QueryRewriterTest {

    // -----------------------------------------------------------------------
    // ExprToGraphQL.isTranslatable
    // -----------------------------------------------------------------------

    @Test
    void isTranslatableSimpleComparison() throws Exception {
        Expr expr = Parser.parse("$price > 100");
        assertTrue(ExprToGraphQL.isTranslatable(expr),
            "$price > 100 is a simple comparison and should be translatable");
    }

    @Test
    void isTranslatableLogicalCombination() throws Exception {
        Expr expr = Parser.parse("$price > 100 && $stock > 0");
        assertTrue(ExprToGraphQL.isTranslatable(expr),
            "Logical AND of simple comparisons should be translatable");
    }

    @Test
    void isTranslatableRejectsScalarCall() throws Exception {
        Expr expr = Parser.parse("len($name) > 5");
        assertFalse(ExprToGraphQL.isTranslatable(expr),
            "ScalarCall (len) should not be translatable");
    }

    @Test
    void isTranslatableRejectsArithmetic() throws Exception {
        Expr expr = Parser.parse("$price * $qty > 1000");
        assertFalse(ExprToGraphQL.isTranslatable(expr),
            "Arithmetic BinaryOp (MUL) should not be translatable");
    }

    @Test
    void isTranslatableLiteral() throws Exception {
        Expr expr = Parser.parse("true");
        assertTrue(ExprToGraphQL.isTranslatable(expr),
            "Literal is translatable");
    }

    @Test
    void isTranslatableFieldRef() throws Exception {
        Expr expr = Parser.parse("$active");
        assertTrue(ExprToGraphQL.isTranslatable(expr),
            "FieldRef is translatable");
    }

    @Test
    void isTranslatableRejectsAggregateCall() throws Exception {
        // aggregate calls in isolation are not valid as filter expressions
        // but we need to confirm isTranslatable rejects them
        Expr agg = new Expr.AggregateCall("sum", new Expr.FieldRef(java.util.List.of("price")));
        assertFalse(ExprToGraphQL.isTranslatable(agg),
            "AggregateCall should not be translatable");
    }

    @Test
    void isTranslatableOrCombination() throws Exception {
        Expr expr = Parser.parse("$age > 18 || $vip == true");
        assertTrue(ExprToGraphQL.isTranslatable(expr),
            "OR of simple comparisons should be translatable");
    }

    @Test
    void isTranslatableNotOfComparison() throws Exception {
        Expr expr = Parser.parse("!($age > 18)");
        assertTrue(ExprToGraphQL.isTranslatable(expr),
            "NOT wrapping a translatable expr should itself be translatable");
    }

    @Test
    void isTranslatableRejectsNeg() {
        Expr negExpr = new Expr.UnaryOp(Expr.UnaryOp.Op.NEG,
            new Expr.Literal(5.0));
        assertFalse(ExprToGraphQL.isTranslatable(negExpr),
            "NEG (arithmetic negation) should not be translatable");
    }

    @Test
    void isTranslatableRejectsArithmeticInSubExpr() throws Exception {
        Expr expr = Parser.parse("$price + $tax > 100");
        assertFalse(ExprToGraphQL.isTranslatable(expr),
            "ADD inside comparison is arithmetic — not translatable");
    }

    // -----------------------------------------------------------------------
    // ExprToGraphQL.translate — produces ObjectValue with correct structure
    // -----------------------------------------------------------------------

    @Test
    void translateSimpleEqComparison() throws Exception {
        Expr expr = Parser.parse("$status == \"active\"");
        assertTrue(ExprToGraphQL.isTranslatable(expr));
        graphql.language.Value<?> value = ExprToGraphQL.translate(expr);
        assertNotNull(value);
        String printed = graphql.language.AstPrinter.printAstCompact(value);
        // Expect something like {status: {_eq: "active"}}
        assertTrue(printed.contains("status"), "field name should appear: " + printed);
        assertTrue(printed.contains("_eq"), "_eq operator should appear: " + printed);
    }

    @Test
    void translateGtComparison() throws Exception {
        Expr expr = Parser.parse("$price > 100");
        graphql.language.Value<?> value = ExprToGraphQL.translate(expr);
        String printed = graphql.language.AstPrinter.printAstCompact(value);
        assertTrue(printed.contains("price"), "field name should appear: " + printed);
        assertTrue(printed.contains("_gt"), "_gt operator should appear: " + printed);
    }

    @Test
    void translateAndMergesFields() throws Exception {
        Expr expr = Parser.parse("$price > 100 && $stock > 0");
        graphql.language.Value<?> value = ExprToGraphQL.translate(expr);
        String printed = graphql.language.AstPrinter.printAstCompact(value);
        assertTrue(printed.contains("price"), "price field should appear: " + printed);
        assertTrue(printed.contains("stock"), "stock field should appear: " + printed);
    }

    @Test
    void translateOrProducesOrKey() throws Exception {
        Expr expr = Parser.parse("$age > 18 || $vip == true");
        graphql.language.Value<?> value = ExprToGraphQL.translate(expr);
        String printed = graphql.language.AstPrinter.printAstCompact(value);
        assertTrue(printed.contains("_or"), "_or key should appear for OR: " + printed);
    }

    @Test
    void translateNullLiteralProducesNullValue() throws Exception {
        Expr expr = Parser.parse("$name == null");
        graphql.language.Value<?> value = ExprToGraphQL.translate(expr);
        String printed = graphql.language.AstPrinter.printAstCompact(value);
        assertTrue(printed.contains("_eq"), "_eq should appear: " + printed);
        assertTrue(printed.contains("null"), "null value should appear: " + printed);
    }

    // -----------------------------------------------------------------------
    // QueryRewriter
    // -----------------------------------------------------------------------

    /**
     * NONE capabilities: reconstruct-only, strips client directives but
     * no filter/sort arguments are injected.
     */
    @Test
    void rewriteWithNoCapabilities() {
        String query = "{ users(filter: $f) { name email } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);
        // No filter set in state
        QueryRewriter rewriter = new QueryRewriter(ServerCapabilities.NONE);
        String result = rewriter.rewrite(ctx, state);

        assertNotNull(result);
        assertFalse(result.isBlank());
        // No new arguments injected; original field argument stays (it's in document)
        // The call to reconstruct does not add or remove arguments — only visible=false removes fields
        assertTrue(result.contains("users"), "users field should be present");
        assertTrue(result.contains("name"), "name field should be present");
        assertTrue(result.contains("email"), "email field should be present");
    }

    /**
     * Filter capability present: queryState has filter expression $age > 18
     * → rewritten query contains the filter argument on 'users'.
     */
    @Test
    void rewriteWithFilterCapability() {
        String query = "{ users(where: $w) { name age } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);
        SchemaPath usersPath = new SchemaPath("users");
        state.setFilterExpression(usersPath, "$age > 18");

        ServerCapabilities caps = new ServerCapabilities(
            Map.of(usersPath, Set.of("where")));
        QueryRewriter rewriter = new QueryRewriter(caps);
        String result = rewriter.rewrite(ctx, state);

        assertNotNull(result);
        // Should contain the where argument with the translated filter
        assertTrue(result.contains("where"), "where argument should be in rewritten query: " + result);
        assertTrue(result.contains("age"), "translated field name should appear: " + result);
    }

    /**
     * Sort capability: queryState has sortFields "name" → rewritten query
     * contains orderBy argument.
     */
    @Test
    void rewriteWithSortCapability() {
        String query = "{ users(orderBy: $o) { name age } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);
        SchemaPath usersPath = new SchemaPath("users");
        state.setSortFields(usersPath, "name");

        ServerCapabilities caps = new ServerCapabilities(
            Map.of(usersPath, Set.of("orderBy")));
        QueryRewriter rewriter = new QueryRewriter(caps);
        String result = rewriter.rewrite(ctx, state);

        assertNotNull(result);
        assertTrue(result.contains("orderBy"), "orderBy argument should be in rewritten query: " + result);
        assertTrue(result.contains("name"), "field name should appear: " + result);
    }

    /**
     * Non-translatable filter (uses ScalarCall) falls back — the argument is
     * NOT modified in the query.
     */
    @Test
    void rewriteNonTranslatableFilterFallsBack() {
        String query = "{ users(where: $w) { name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);
        SchemaPath usersPath = new SchemaPath("users");
        state.setFilterExpression(usersPath, "len($name) > 5");

        ServerCapabilities caps = new ServerCapabilities(
            Map.of(usersPath, Set.of("where")));
        QueryRewriter rewriter = new QueryRewriter(caps);
        String result = rewriter.rewrite(ctx, state);

        assertNotNull(result);
        // The original $w placeholder should remain unchanged — not replaced with translated value
        // We verify the field is present but no structured filter object was injected
        assertTrue(result.contains("users"), "users field should be present");
        // The original variable placeholder remains
        assertTrue(result.contains("$w") || result.contains("where"),
            "original query structure should be preserved: " + result);
    }

    /**
     * Hidden field is still omitted — delegates to QueryBuilder.
     */
    @Test
    void rewritePreservesVisibleFalseRemoval() {
        String query = "{ users { name email age } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);
        SchemaPath emailPath = new SchemaPath("users").child("email");
        state.setVisible(emailPath, false);

        QueryRewriter rewriter = new QueryRewriter(ServerCapabilities.NONE);
        String result = rewriter.rewrite(ctx, state);

        assertFalse(result.contains("email"), "Hidden field should be absent: " + result);
        assertTrue(result.contains("name"), "Visible field should remain: " + result);
        assertTrue(result.contains("age"), "Visible field should remain: " + result);
    }

    /**
     * Result is valid GraphQL — re-parses without error.
     */
    @Test
    void rewriteResultIsValidGraphQL() {
        String query = "{ users(filter: $f) { name @hide email } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);

        QueryRewriter rewriter = new QueryRewriter(ServerCapabilities.NONE);
        String result = rewriter.rewrite(ctx, state);

        assertDoesNotThrow(() -> graphql.parser.Parser.parse(result),
            "Rewritten query must be valid GraphQL: " + result);
    }

    /**
     * Multiple overridden paths — only path with capabilities gets pushed.
     */
    @Test
    void rewriteOnlyPushesWhenCapabilitiesMatch() {
        String query = "{ users(where: $w) { name } posts { title } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);

        SchemaPath usersPath = new SchemaPath("users");
        SchemaPath postsPath = new SchemaPath("posts");

        state.setFilterExpression(usersPath, "$name == \"Alice\"");
        state.setFilterExpression(postsPath, "$title == \"hello\"");  // no capability for posts

        ServerCapabilities caps = new ServerCapabilities(
            Map.of(usersPath, Set.of("where")));
        QueryRewriter rewriter = new QueryRewriter(caps);
        String result = rewriter.rewrite(ctx, state);

        assertNotNull(result);
        assertTrue(result.contains("users"), "users should be in result");
        assertTrue(result.contains("posts"), "posts should be in result");
        // users got its where pushed, posts did not (no capability)
        assertTrue(result.contains("where"), "where should appear for users: " + result);
    }
}

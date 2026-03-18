// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.query.LayoutQueryState;

/**
 * End-to-end tests for RDR-028 Phase 3c — double-evaluation prevention.
 *
 * <p>Verifies that when QueryRewriter pushes a filter or sort server-side,
 * the corresponding expression is nulled on LayoutQueryState (preventing
 * client-side re-evaluation), and that the internal pushedFilters/pushedSorts
 * maps retain the original expressions.
 */
class QueryRewriterE2ETest {

    // -----------------------------------------------------------------------
    // 1. pushFilterNullsClientExpression
    // -----------------------------------------------------------------------

    @Test
    void pushFilterNullsClientExpression() {
        String query = "{ users(where: $w) { name age } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);
        SchemaPath usersPath = new SchemaPath("users");
        state.setFilterExpression(usersPath, "$age > 18");

        ServerCapabilities caps = new ServerCapabilities(
            Map.of(usersPath, Set.of("where")));
        QueryRewriter rewriter = new QueryRewriter(caps);
        rewriter.rewrite(ctx, state);

        // After rewrite, the filter expression should be nulled on queryState
        // so client-side evaluation does not double-apply it.
        assertNull(state.getFieldState(usersPath).filterExpression(),
            "filterExpression should be null after server push (double-evaluation prevention)");
    }

    // -----------------------------------------------------------------------
    // 2. pushSortNullsClientSortFields
    // -----------------------------------------------------------------------

    @Test
    void pushSortNullsClientSortFields() {
        String query = "{ users(orderBy: $o) { name age } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);
        SchemaPath usersPath = new SchemaPath("users");
        state.setSortFields(usersPath, "name,-age");

        ServerCapabilities caps = new ServerCapabilities(
            Map.of(usersPath, Set.of("orderBy")));
        QueryRewriter rewriter = new QueryRewriter(caps);
        rewriter.rewrite(ctx, state);

        // After rewrite, sortFields should be nulled on queryState.
        assertNull(state.getFieldState(usersPath).sortFields(),
            "sortFields should be null after server push (double-evaluation prevention)");
    }

    // -----------------------------------------------------------------------
    // 3. pushedFilterRetainedInternally
    // -----------------------------------------------------------------------

    @Test
    void pushedFilterRetainedInternally() {
        String query = "{ users(where: $w) { name age } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);
        SchemaPath usersPath = new SchemaPath("users");
        state.setFilterExpression(usersPath, "$age > 18");

        ServerCapabilities caps = new ServerCapabilities(
            Map.of(usersPath, Set.of("where")));
        QueryRewriter rewriter = new QueryRewriter(caps);
        String result = rewriter.rewrite(ctx, state);

        // The rewritten query should contain the server-side filter argument
        assertTrue(result.contains("where"),
            "Rewritten query should contain 'where' argument: " + result);
        assertTrue(result.contains("age"),
            "Rewritten query should reference the 'age' field in the filter: " + result);

        // The internal map retains the original expression
        assertEquals("$age > 18", rewriter.getPushedFilter(usersPath),
            "pushedFilters should retain the original expression");
    }

    // -----------------------------------------------------------------------
    // 4. nonPushableFilterRemainsOnClient
    // -----------------------------------------------------------------------

    @Test
    void nonPushableFilterRemainsOnClient() {
        String query = "{ posts { title author } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);
        SchemaPath postsPath = new SchemaPath("posts");
        state.setFilterExpression(postsPath, "$author == \"Alice\"");

        // No capabilities for posts — cannot push
        QueryRewriter rewriter = new QueryRewriter(ServerCapabilities.NONE);
        rewriter.rewrite(ctx, state);

        // Expression must remain on queryState for client-side evaluation
        assertEquals("$author == \"Alice\"",
            state.getFieldState(postsPath).filterExpression(),
            "Filter expression should remain on client when push is not supported");

        // Nothing retained in pushedFilters
        assertNull(rewriter.getPushedFilter(postsPath),
            "pushedFilters should be empty when push is not supported");
    }

    // -----------------------------------------------------------------------
    // 5. clearPushedFilterRestoresClientSide
    // -----------------------------------------------------------------------

    @Test
    void clearPushedFilterRestoresClientSide() {
        String query = "{ users(where: $w) { name age } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);
        SchemaPath usersPath = new SchemaPath("users");
        state.setFilterExpression(usersPath, "$age > 18");

        ServerCapabilities caps = new ServerCapabilities(
            Map.of(usersPath, Set.of("where")));
        QueryRewriter rewriter = new QueryRewriter(caps);

        // First rewrite: push happens, client expression nulled
        rewriter.rewrite(ctx, state);
        assertNull(state.getFieldState(usersPath).filterExpression(),
            "Precondition: expression should be null after first push");
        assertNotNull(rewriter.getPushedFilter(usersPath),
            "Precondition: pushedFilters should hold the expression");

        // User explicitly clears the filter (sets null directly)
        state.setFilterExpression(usersPath, null);

        // Second rewrite: path has no expression (user cleared it) → drop from pushed map
        rewriter.rewrite(ctx, state);

        assertNull(rewriter.getPushedFilter(usersPath),
            "pushedFilters should be empty after user clears the filter");
    }

    // -----------------------------------------------------------------------
    // 6. rewriteProducesValidGraphQL — filter + sort + hidden field
    // -----------------------------------------------------------------------

    @Test
    void rewriteProducesValidGraphQL() {
        String query = "{ users(where: $w, orderBy: $o) { name age email } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        LayoutQueryState state = new LayoutQueryState(null);
        SchemaPath usersPath = new SchemaPath("users");
        SchemaPath emailPath = usersPath.child("email");

        state.setFilterExpression(usersPath, "$age > 18");
        state.setSortFields(usersPath, "name");
        state.setVisible(emailPath, false);

        ServerCapabilities caps = new ServerCapabilities(
            Map.of(usersPath, Set.of("where", "orderBy")));
        QueryRewriter rewriter = new QueryRewriter(caps);
        String result = rewriter.rewrite(ctx, state);

        // Result must parse as valid GraphQL
        assertDoesNotThrow(() -> graphql.parser.Parser.parse(result),
            "Rewritten query must be valid GraphQL: " + result);

        // Filter argument injected
        assertTrue(result.contains("where"),
            "where argument should be present: " + result);

        // Sort argument injected
        assertTrue(result.contains("orderBy"),
            "orderBy argument should be present: " + result);

        // Hidden field absent
        assertFalse(result.contains("email"),
            "Hidden field 'email' should be absent: " + result);

        // Visible fields present
        assertTrue(result.contains("name"),
            "'name' should be present: " + result);
        assertTrue(result.contains("age"),
            "'age' should be present in the filter argument: " + result);

        // Client-side expressions nulled after push
        assertNull(state.getFieldState(usersPath).filterExpression(),
            "filterExpression should be nulled after push");
        assertNull(state.getFieldState(usersPath).sortFields(),
            "sortFields should be nulled after push");
    }
}

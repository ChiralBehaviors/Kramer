// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.DefaultLayoutStylesheet;
import com.chiralbehaviors.layout.LayoutStylesheet;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;

import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.StringValue;
import graphql.parser.Parser;

/**
 * TDD tests for RelayConnectionDetector and SchemaContext.withCursor() (RDR-020 Phase D2).
 */
class RelayPaginationTest {

    // Minimal all-visible stylesheet
    private static final LayoutStylesheet ALL_VISIBLE = new LayoutStylesheet() {
        @Override public long getVersion()                                           { return 1L; }
        @Override public double getDouble(SchemaPath p, String k, double d)         { return d; }
        @Override public int getInt(SchemaPath p, String k, int d)                  { return d; }
        @Override public String getString(SchemaPath p, String k, String d)         { return d; }
        @Override public boolean getBoolean(SchemaPath p, String k, boolean d)      { return d; }
        @Override public PrimitiveStyle primitiveStyle(SchemaPath p)                { return null; }
        @Override public RelationStyle relationStyle(SchemaPath p)                   { return null; }
    };

    // Helper: build a Relation tree for a standard Relay connection
    private static Relation buildConnectionRelation(String name) {
        Relation conn = new Relation(name);

        Relation edges = new Relation("edges");
        Relation node = new Relation("node");
        node.addChild(new Primitive("id"));
        node.addChild(new Primitive("name"));
        edges.addChild(node);
        conn.addChild(edges);

        Relation pageInfo = new Relation("pageInfo");
        pageInfo.addChild(new Primitive("endCursor"));
        pageInfo.addChild(new Primitive("hasNextPage"));
        conn.addChild(pageInfo);

        return conn;
    }

    // -------------------------------------------------------------------
    // RelayConnectionDetector tests
    // -------------------------------------------------------------------

    @Test
    void isConnectionPositive() {
        Relation conn = buildConnectionRelation("users");
        assertTrue(RelayConnectionDetector.isConnection(conn),
            "Relation with edges{node{...}} + pageInfo should be detected as connection");
    }

    @Test
    void isConnectionNoEdges() {
        Relation rel = new Relation("users");
        rel.addChild(new Primitive("name"));
        Relation pageInfo = new Relation("pageInfo");
        pageInfo.addChild(new Primitive("endCursor"));
        rel.addChild(pageInfo);

        assertFalse(RelayConnectionDetector.isConnection(rel),
            "Relation without edges should not be a connection");
    }

    @Test
    void isConnectionNoNode() {
        Relation rel = new Relation("users");

        Relation edges = new Relation("edges");
        edges.addChild(new Primitive("cursor")); // no 'node' child
        rel.addChild(edges);

        Relation pageInfo = new Relation("pageInfo");
        pageInfo.addChild(new Primitive("endCursor"));
        rel.addChild(pageInfo);

        assertFalse(RelayConnectionDetector.isConnection(rel),
            "edges without node grandchild should not be a connection");
    }

    @Test
    void isConnectionNoPageInfo() {
        Relation rel = new Relation("users");

        Relation edges = new Relation("edges");
        Relation node = new Relation("node");
        node.addChild(new Primitive("name"));
        edges.addChild(node);
        rel.addChild(edges);
        // no pageInfo child

        assertFalse(RelayConnectionDetector.isConnection(rel),
            "edges{node{...}} without pageInfo should not be a connection");
    }

    // -------------------------------------------------------------------
    // SchemaContext.withCursor() tests
    // -------------------------------------------------------------------

    private static final String QUERY_NO_AFTER =
        "{ users(first: 10) { edges { node { name } } pageInfo { endCursor hasNextPage } } }";

    private static final String QUERY_WITH_AFTER =
        "{ users(first: 10, after: \"oldCursor\") { edges { node { name } } pageInfo { endCursor hasNextPage } } }";

    @Test
    void withCursorAddsAfterArg() {
        SchemaContext ctx = GraphQlUtil.buildContext(QUERY_NO_AFTER);
        SchemaPath usersPath = new SchemaPath("users");

        SchemaContext updated = ctx.withCursor(usersPath, "cursor123");

        Field updatedField = updated.fieldAt(usersPath).orElseThrow();
        boolean hasAfter = updatedField.getArguments().stream()
            .anyMatch(a -> "after".equals(a.getName()));
        assertTrue(hasAfter, "withCursor should add 'after' argument when not present");

        String afterValue = updatedField.getArguments().stream()
            .filter(a -> "after".equals(a.getName()))
            .map(a -> ((StringValue) a.getValue()).getValue())
            .findFirst().orElseThrow();
        assertEquals("cursor123", afterValue);
    }

    @Test
    void withCursorReplacesExistingAfter() {
        SchemaContext ctx = GraphQlUtil.buildContext(QUERY_WITH_AFTER);
        SchemaPath usersPath = new SchemaPath("users");

        // Verify original has "oldCursor"
        Field original = ctx.fieldAt(usersPath).orElseThrow();
        String oldVal = original.getArguments().stream()
            .filter(a -> "after".equals(a.getName()))
            .map(a -> ((StringValue) a.getValue()).getValue())
            .findFirst().orElseThrow();
        assertEquals("oldCursor", oldVal);

        SchemaContext updated = ctx.withCursor(usersPath, "newCursor");

        Field updatedField = updated.fieldAt(usersPath).orElseThrow();
        long afterCount = updatedField.getArguments().stream()
            .filter(a -> "after".equals(a.getName()))
            .count();
        assertEquals(1, afterCount, "Should have exactly one 'after' argument");

        String newVal = updatedField.getArguments().stream()
            .filter(a -> "after".equals(a.getName()))
            .map(a -> ((StringValue) a.getValue()).getValue())
            .findFirst().orElseThrow();
        assertEquals("newCursor", newVal);
    }

    @Test
    void withCursorImmutable() {
        SchemaContext ctx = GraphQlUtil.buildContext(QUERY_NO_AFTER);
        SchemaPath usersPath = new SchemaPath("users");

        ctx.withCursor(usersPath, "cursor123");

        // Original context must be unchanged
        Field originalField = ctx.fieldAt(usersPath).orElseThrow();
        boolean hadAfter = originalField.getArguments().stream()
            .anyMatch(a -> "after".equals(a.getName()));
        assertFalse(hadAfter, "Original SchemaContext fieldIndex must not be mutated");
    }

    @Test
    void withCursorRoundTrip() {
        SchemaContext ctx = GraphQlUtil.buildContext(QUERY_NO_AFTER);
        SchemaPath usersPath = new SchemaPath("users");

        SchemaContext updated = ctx.withCursor(usersPath, "pageCursor42");
        String reconstructed = new QueryBuilder().reconstruct(updated, ALL_VISIBLE);

        // Re-parse and verify "after" argument is present with correct value
        assertDoesNotThrow(() -> Parser.parse(reconstructed), "Reconstructed query must be valid GraphQL");
        assertTrue(reconstructed.contains("after"), "Reconstructed query must contain 'after' argument");
        assertTrue(reconstructed.contains("pageCursor42"), "Reconstructed query must contain cursor value");
    }

    @Test
    void withCursorUnknownPathThrows() {
        SchemaContext ctx = GraphQlUtil.buildContext(QUERY_NO_AFTER);
        SchemaPath unknown = new SchemaPath("nonexistent");

        assertThrows(IllegalArgumentException.class,
            () -> ctx.withCursor(unknown, "cursor"),
            "withCursor with unknown path should throw IllegalArgumentException");
    }
}

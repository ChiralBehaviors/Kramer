// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.DefaultLayoutStylesheet;
import com.chiralbehaviors.layout.LayoutPropertyKeys;
import com.chiralbehaviors.layout.LayoutStylesheet;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;

import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;

/**
 * TDD tests for QueryBuilder (RDR-020 Phase C).
 */
class QueryBuilderTest {

    // Minimal all-visible stylesheet (all defaults)
    private static final LayoutStylesheet ALL_VISIBLE = new LayoutStylesheet() {
        @Override public long getVersion()                                              { return 1L; }
        @Override public double getDouble(SchemaPath p, String k, double d)            { return d; }
        @Override public int getInt(SchemaPath p, String k, int d)                     { return d; }
        @Override public String getString(SchemaPath p, String k, String d)            { return d; }
        @Override public boolean getBoolean(SchemaPath p, String k, boolean d)         { return d; }
        @Override public PrimitiveStyle primitiveStyle(SchemaPath p)                   { return null; }
        @Override public RelationStyle relationStyle(SchemaPath p)                     { return null; }
    };

    private static Set<String> fieldsInOutput(String queryString) {
        Document doc = Parser.parse(queryString);
        var op = doc.getDefinitions().stream()
                    .filter(OperationDefinition.class::isInstance)
                    .map(OperationDefinition.class::cast)
                    .findFirst().orElseThrow();
        return collectFieldNames(op.getSelectionSet());
    }

    private static Set<String> collectFieldNames(graphql.language.SelectionSet ss) {
        if (ss == null) return Set.of();
        var names = new java.util.HashSet<String>();
        for (var sel : ss.getSelections()) {
            if (sel instanceof graphql.language.Field f) {
                names.add(f.getName());
                names.addAll(collectFieldNames(f.getSelectionSet()));
            }
        }
        return names;
    }

    // 1. roundTripAllVisible — all fields present after reconstruct
    @Test
    void roundTripAllVisible() {
        String query = "{ users { name email } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        QueryBuilder qb = new QueryBuilder();

        String result = qb.reconstruct(ctx, ALL_VISIBLE);

        assertNotNull(result);
        assertFalse(result.isBlank());

        // Re-parse and verify fields survive the round-trip
        Set<String> fields = fieldsInOutput(result);
        assertTrue(fields.contains("name"), "name should be present");
        assertTrue(fields.contains("email"), "email should be present");
    }

    // 2. hiddenFieldOmitted — visible=false causes field to be absent
    @Test
    void hiddenFieldOmitted() {
        String query = "{ users { name email age } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null) {
            @Override
            public boolean getBoolean(SchemaPath path, String property, boolean defaultValue) {
                if (LayoutPropertyKeys.VISIBLE.equals(property)
                        && "email".equals(path.leaf())) {
                    return false;
                }
                return defaultValue;
            }
        };

        String result = new QueryBuilder().reconstruct(ctx, sheet);

        assertFalse(result.contains("email"), "Hidden field should be absent");
        assertTrue(result.contains("name"),   "Visible field should remain");
        assertTrue(result.contains("age"),    "Visible field should remain");
    }

    // 3. argumentsPreserved — field arguments survive reconstruction
    @Test
    void argumentsPreserved() {
        String query = "{ users(limit: 10, offset: 5) { name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        String result = new QueryBuilder().reconstruct(ctx, ALL_VISIBLE);

        assertTrue(result.contains("limit"),  "Argument 'limit' should be preserved");
        assertTrue(result.contains("10"),     "Argument value 10 should be preserved");
        assertTrue(result.contains("offset"), "Argument 'offset' should be preserved");
        assertTrue(result.contains("5"),      "Argument value 5 should be preserved");
    }

    // 4. clientDirectivesStripped — @hide removed from output
    @Test
    void clientDirectivesStripped() {
        String query = "{ users { name @hide email } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        String result = new QueryBuilder().reconstruct(ctx, ALL_VISIBLE);

        assertFalse(result.contains("@hide"), "@hide must be stripped from output");
        assertTrue(result.contains("name"),   "field itself should still be present");
    }

    // 5. nonClientDirectivePreserved — @deprecated survives
    @Test
    void nonClientDirectivePreserved() {
        String query = "{ users { oldField @deprecated name } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        String result = new QueryBuilder().reconstruct(ctx, ALL_VISIBLE);

        assertTrue(result.contains("@deprecated"), "@deprecated should be preserved");
    }

    // 6. customStripList — @custom stripped, @hide preserved
    @Test
    void customStripList() {
        String query = "{ users { name @custom email @hide } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        QueryBuilder qb = new QueryBuilder(Set.of("custom"));
        String result = qb.reconstruct(ctx, ALL_VISIBLE);

        assertFalse(result.contains("@custom"), "@custom should be stripped with custom list");
        assertTrue(result.contains("@hide"),    "@hide should be preserved with custom list");
    }

    // 7. nestedFieldHiding — parent stays, hidden child absent
    @Test
    void nestedFieldHiding() {
        String query = "{ users { name orders { total amount } } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null) {
            @Override
            public boolean getBoolean(SchemaPath path, String property, boolean defaultValue) {
                if (LayoutPropertyKeys.VISIBLE.equals(property)
                        && "total".equals(path.leaf())) {
                    return false;
                }
                return defaultValue;
            }
        };

        String result = new QueryBuilder().reconstruct(ctx, sheet);

        assertTrue(result.contains("users"),   "parent relation should be present");
        assertTrue(result.contains("orders"),  "parent relation should be present");
        assertTrue(result.contains("amount"),  "visible nested field should remain");
        assertFalse(result.contains("total"),  "hidden nested field should be absent");
    }

    // 8. resultIsValidGraphQL — reconstructed string re-parses without error
    @Test
    void resultIsValidGraphQL() {
        String query = "{ users(limit: 5) { name @hide email orders { total @render(mode: \"LIST\") } } }";
        SchemaContext ctx = GraphQlUtil.buildContext(query);

        String result = new QueryBuilder().reconstruct(ctx, ALL_VISIBLE);

        // AstPrinter output must be parseable
        assertDoesNotThrow(() -> Parser.parse(result), "Output must be valid GraphQL");
    }
}

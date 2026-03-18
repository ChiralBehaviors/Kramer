// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.LayoutPropertyKeys;
import com.chiralbehaviors.layout.SchemaPath;

/**
 * TDD tests for DirectiveReader and DirectiveAwareStylesheet (RDR-020 Phase B).
 */
class DirectiveTest {

    // --- Stub inner stylesheet for controlled testing ---

    private static final class StubStylesheet implements com.chiralbehaviors.layout.LayoutStylesheet {
        private final long    version;
        private final boolean defaultBoolean;
        private final String  defaultString;

        StubStylesheet(long version, boolean defaultBoolean, String defaultString) {
            this.version       = version;
            this.defaultBoolean = defaultBoolean;
            this.defaultString  = defaultString;
        }

        @Override public long getVersion()                                              { return version; }
        @Override public double getDouble(SchemaPath p, String k, double d)             { return d; }
        @Override public int getInt(SchemaPath p, String k, int d)                      { return d; }
        @Override public String getString(SchemaPath p, String k, String d)             { return defaultString; }
        @Override public boolean getBoolean(SchemaPath p, String k, boolean d)          { return defaultBoolean; }
        @Override public com.chiralbehaviors.layout.style.PrimitiveStyle primitiveStyle(SchemaPath p) { return null; }
        @Override public com.chiralbehaviors.layout.style.RelationStyle relationStyle(SchemaPath p)   { return null; }
    }

    // --- Helper: build a DirectiveAwareStylesheet from a query string ---

    private DirectiveAwareStylesheet buildSheet(String query, com.chiralbehaviors.layout.LayoutStylesheet inner) {
        SchemaContext ctx = GraphQlUtil.buildContext(query);
        DirectiveReader reader = new DirectiveReader();
        return new DirectiveAwareStylesheet(inner, reader, ctx);
    }

    // 1. @hide → getBoolean(path, "visible", true) returns false
    @Test
    void hideDirectiveSetsVisibleFalse() {
        String query = "{ users { name @hide email } }";
        var inner = new StubStylesheet(1L, true, "default");
        DirectiveAwareStylesheet sheet = buildSheet(query, inner);

        SchemaPath namePath = new SchemaPath("users", "name");
        assertFalse(sheet.getBoolean(namePath, LayoutPropertyKeys.VISIBLE, true),
            "@hide should set visible to false");

        // unaffected field delegates
        SchemaPath emailPath = new SchemaPath("users", "email");
        assertTrue(sheet.getBoolean(emailPath, LayoutPropertyKeys.VISIBLE, true),
            "Field without @hide should delegate to inner (true)");
    }

    // 2. @render(mode: "SPARKLINE") → getString returns "SPARKLINE"
    @Test
    void renderDirectiveSetsRenderMode() {
        String query = "{ users { score @render(mode: \"SPARKLINE\") } }";
        var inner = new StubStylesheet(1L, true, null);
        DirectiveAwareStylesheet sheet = buildSheet(query, inner);

        SchemaPath scorePath = new SchemaPath("users", "score");
        assertEquals("SPARKLINE",
            sheet.getString(scorePath, LayoutPropertyKeys.RENDER_MODE, null),
            "@render(mode: \"SPARKLINE\") should return SPARKLINE");
    }

    // 3. @hideIfEmpty → getBoolean(path, "hide-if-empty", false) returns true
    @Test
    void hideIfEmptyDirectiveSetsProperty() {
        String query = "{ orders { notes @hideIfEmpty } }";
        var inner = new StubStylesheet(1L, false, null);
        DirectiveAwareStylesheet sheet = buildSheet(query, inner);

        SchemaPath notesPath = new SchemaPath("orders", "notes");
        assertTrue(sheet.getBoolean(notesPath, LayoutPropertyKeys.HIDE_IF_EMPTY, false),
            "@hideIfEmpty should set hide-if-empty to true");
    }

    // 4. Field without directives delegates to inner
    @Test
    void noDirectiveDelegatesToInner() {
        String query = "{ users { name email } }";
        var inner = new StubStylesheet(42L, false, "inner-value");
        DirectiveAwareStylesheet sheet = buildSheet(query, inner);

        SchemaPath namePath = new SchemaPath("users", "name");
        assertFalse(sheet.getBoolean(namePath, LayoutPropertyKeys.VISIBLE, true),
            "Should delegate to inner boolean");
        assertEquals("inner-value",
            sheet.getString(namePath, LayoutPropertyKeys.RENDER_MODE, "default"),
            "Should delegate to inner string");
    }

    // 5. Directive takes precedence over inner stylesheet value
    @Test
    void directivePrecedenceOverInner() {
        String query = "{ users { name @hide } }";
        // inner says visible = true; directive should override to false
        var inner = new StubStylesheet(1L, true, null);
        DirectiveAwareStylesheet sheet = buildSheet(query, inner);

        SchemaPath namePath = new SchemaPath("users", "name");
        assertFalse(sheet.getBoolean(namePath, LayoutPropertyKeys.VISIBLE, true),
            "Directive must win over inner stylesheet");
    }

    // 6. getVersion() delegates to inner
    @Test
    void versionDelegatesToInner() {
        String query = "{ users { name } }";
        var inner = new StubStylesheet(99L, true, null);
        DirectiveAwareStylesheet sheet = buildSheet(query, inner);

        assertEquals(99L, sheet.getVersion(), "getVersion should delegate to inner");
    }

    // 7. Unknown directive is silently ignored — no override, no exception
    @Test
    void unknownDirectiveIgnored() {
        String query = "{ users { name @foo } }";
        var inner = new StubStylesheet(1L, true, "default");
        DirectiveAwareStylesheet sheet = buildSheet(query, inner);

        SchemaPath namePath = new SchemaPath("users", "name");
        // @foo is unknown; should not change visible (delegates to inner = true)
        assertTrue(sheet.getBoolean(namePath, LayoutPropertyKeys.VISIBLE, true),
            "Unknown directives should not affect property values");
    }
}

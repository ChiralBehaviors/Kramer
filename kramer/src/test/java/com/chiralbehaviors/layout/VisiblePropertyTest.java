// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tests for Kramer-9cy: visible property in the layout pipeline.
 * visible=true (default) includes field; visible=false excludes it.
 */
class VisiblePropertyTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Style mockModel(Relation schema) {
        Style model = mock(Style.class);
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        RelationStyle relStyle   = TestLayouts.mockRelationStyle();

        when(model.layout(any(SchemaNode.class))).thenAnswer(inv -> {
            SchemaNode n = inv.getArgument(0);
            if (n instanceof Primitive p) return new PrimitiveLayout(p, primStyle);
            if (n instanceof Relation  r) return new RelationLayout(r, relStyle);
            return null;
        });
        return model;
    }

    private RelationLayout buildLayout(Relation schema) {
        RelationStyle style = TestLayouts.mockRelationStyle();
        return new RelationLayout(schema, style);
    }

    /** Build a single-row ArrayNode with name + description fields. */
    private ArrayNode buildData(String name, String description) {
        ObjectNode row = JsonNodeFactory.instance.objectNode();
        row.put("name",        name);
        row.put("description", description);
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add(row);
        return data;
    }

    // -----------------------------------------------------------------------
    // Test: visible=true (default) includes all children in layout
    // -----------------------------------------------------------------------

    /**
     * When no visible override is set, both children must appear in the
     * children list after measure().
     */
    @Test
    void visibleTrueDefaultIncludesAllChildren() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("description"));

        RelationLayout layout = buildLayout(schema);

        // Wire a real Style with a real DefaultLayoutStylesheet — no overrides.
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        Style model = mockModel(schema);
        when(model.getStylesheet()).thenReturn(sheet);

        SchemaPath root = new SchemaPath("items");
        layout.setSchemaPath(root);

        ArrayNode data = buildData("Alice", "A longer description");
        layout.measure(data, n -> n, model);

        // Both children must be present
        assertEquals(2, layout.getChildren().size(),
                     "visible=true (default) must include both children");
    }

    // -----------------------------------------------------------------------
    // Test: visible=false excludes the child from RelationLayout iteration
    // -----------------------------------------------------------------------

    /**
     * When visible=false is set on a child's path, that child must be skipped
     * during measure() — it does not appear in children and does not
     * contribute to columnWidth.
     */
    @Test
    void visibleFalseExcludesChildFromRelationLayout() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("description"));

        RelationLayout layout = buildLayout(schema);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        // Mark the "description" child as not visible.
        SchemaPath descPath = new SchemaPath("items", "description");
        sheet.setOverride(descPath, LayoutPropertyKeys.VISIBLE, false);

        Style model = mockModel(schema);
        when(model.getStylesheet()).thenReturn(sheet);

        SchemaPath root = new SchemaPath("items");
        layout.setSchemaPath(root);

        ArrayNode data = buildData("Alice", "A longer description");
        layout.measure(data, n -> n, model);

        // Only the "name" child should be present
        assertEquals(1, layout.getChildren().size(),
                     "visible=false must exclude child from RelationLayout");
        SchemaNodeLayout only = layout.getChildren().get(0);
        assertTrue(only instanceof PrimitiveLayout,
                   "Remaining child must be PrimitiveLayout for 'name'");
        assertEquals("name", only.getNode().getField(),
                     "Remaining child must be the 'name' field");
    }

    // -----------------------------------------------------------------------
    // Test: visible=false on a primitive produces zero width/height contribution
    // -----------------------------------------------------------------------

    /**
     * When visible=false is set on a PrimitiveLayout's own path, measure()
     * must return 0 (no width contribution).
     */
    @Test
    void visibleFalseOnPrimitiveProducesZeroWidth() {
        Primitive prim = new Primitive("description");
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(prim, primStyle);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("items", "description");
        sheet.setOverride(path, LayoutPropertyKeys.VISIBLE, false);
        layout.setSchemaPath(path);

        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(sheet);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("A very long description that would normally occupy space");

        double width = layout.measure(data, n -> n, model);

        assertEquals(0.0, width, 1e-9,
                     "visible=false on primitive must return 0 width");

        MeasureResult mr = layout.getMeasureResult();
        assertNotNull(mr, "MeasureResult must be non-null even when invisible");
        assertEquals(0.0, mr.columnWidth(), 1e-9,
                     "MeasureResult.columnWidth must be 0 when visible=false");
    }

    // -----------------------------------------------------------------------
    // Test: visible=false does not affect siblings
    // -----------------------------------------------------------------------

    /**
     * Marking one child invisible must not change the measured width of a
     * visible sibling. The visible child's column width must be positive.
     */
    @Test
    void visibleFalseDoesNotAffectSiblings() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("description"));

        RelationLayout layout = buildLayout(schema);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath descPath = new SchemaPath("items", "description");
        sheet.setOverride(descPath, LayoutPropertyKeys.VISIBLE, false);

        Style model = mockModel(schema);
        when(model.getStylesheet()).thenReturn(sheet);

        SchemaPath root = new SchemaPath("items");
        layout.setSchemaPath(root);

        ArrayNode data = buildData("Alice", "A long description text");
        layout.measure(data, n -> n, model);

        assertEquals(1, layout.getChildren().size(),
                     "Only one visible child should remain");
        SchemaNodeLayout visibleChild = layout.getChildren().get(0);
        assertTrue(visibleChild.columnWidth() > 0,
                   "Visible sibling 'name' must have positive column width");
    }

    // -----------------------------------------------------------------------
    // Test: visible=true explicit (same as default) still includes child
    // -----------------------------------------------------------------------

    @Test
    void visibleTrueExplicitIncludesChild() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("description"));

        RelationLayout layout = buildLayout(schema);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        // Explicitly set visible=true (same as the default)
        SchemaPath descPath = new SchemaPath("items", "description");
        sheet.setOverride(descPath, LayoutPropertyKeys.VISIBLE, true);

        Style model = mockModel(schema);
        when(model.getStylesheet()).thenReturn(sheet);

        SchemaPath root = new SchemaPath("items");
        layout.setSchemaPath(root);

        ArrayNode data = buildData("Alice", "A description");
        layout.measure(data, n -> n, model);

        assertEquals(2, layout.getChildren().size(),
                     "visible=true explicit must include both children");
    }
}

// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.Style;

/**
 * Tests that SchemaPath is derived at construction time via buildPaths(),
 * before measure() is called (Kramer-e1l).
 */
class SchemaPathDerivationTest {

    // ---- Helper: build a Style mock that returns real layouts ----

    private Style mockStyle(Relation root) {
        Style model = mock(Style.class);
        // Wire model.layout(SchemaNode) to return new layouts per node
        when(model.layout(any(Primitive.class))).thenAnswer(inv -> {
            Primitive p = inv.getArgument(0);
            return new PrimitiveLayout(p, TestLayouts.mockPrimitiveStyle(7.0));
        });
        when(model.layout(any(Relation.class))).thenAnswer(inv -> {
            Relation r = inv.getArgument(0);
            return new RelationLayout(r, TestLayouts.mockRelationStyle());
        });
        when(model.layout(any(com.chiralbehaviors.layout.schema.SchemaNode.class))).thenAnswer(inv -> {
            var n = inv.getArgument(0);
            if (n instanceof Primitive p) return new PrimitiveLayout(p, TestLayouts.mockPrimitiveStyle(7.0));
            return new RelationLayout((Relation) n, TestLayouts.mockRelationStyle());
        });
        return model;
    }

    @Test
    void rootLayoutHasPathAfterBuildPaths() {
        Relation schema = new Relation("catalog");
        schema.addChild(new Primitive("name"));

        Style model = mockStyle(schema);
        RelationLayout layout = new RelationLayout(schema, TestLayouts.mockRelationStyle());

        // Before buildPaths: path is null
        assertNull(layout.getSchemaPath(), "SchemaPath must be null before buildPaths()");

        // After buildPaths: path is set — no measure() needed
        layout.buildPaths(new SchemaPath("catalog"), model);

        assertNotNull(layout.getSchemaPath(), "SchemaPath must be non-null after buildPaths()");
        assertEquals(new SchemaPath("catalog"), layout.getSchemaPath());
    }

    @Test
    void childLayoutsHaveCorrectPathsAfterBuildPaths() {
        Relation schema = new Relation("catalog");
        Primitive namePrim = new Primitive("name");
        Primitive codePrim = new Primitive("code");
        schema.addChild(namePrim);
        schema.addChild(codePrim);

        // Use a Style that returns stable layouts (same instance per schema node)
        PrimitiveLayout nameLayout = new PrimitiveLayout(namePrim, TestLayouts.mockPrimitiveStyle(7.0));
        PrimitiveLayout codeLayout = new PrimitiveLayout(codePrim, TestLayouts.mockPrimitiveStyle(7.0));
        RelationLayout relLayout = new RelationLayout(schema, TestLayouts.mockRelationStyle());

        Style model = mock(Style.class);
        when(model.layout(namePrim)).thenReturn(nameLayout);
        when(model.layout(codePrim)).thenReturn(codeLayout);
        when(model.layout(any(com.chiralbehaviors.layout.schema.SchemaNode.class))).thenAnswer(inv -> {
            var n = inv.getArgument(0);
            if (n == namePrim) return nameLayout;
            if (n == codePrim) return codeLayout;
            return relLayout;
        });

        relLayout.buildPaths(new SchemaPath("catalog"), model);

        // Root
        assertEquals(new SchemaPath("catalog"), relLayout.getSchemaPath());

        // Children via schema traversal
        assertEquals(new SchemaPath("catalog").child("name"), nameLayout.getSchemaPath());
        assertEquals(new SchemaPath("catalog").child("code"), codeLayout.getSchemaPath());
    }

    @Test
    void deepNestingPathsCorrect() {
        // catalog -> items (Relation) -> id (Primitive)
        Relation catalog = new Relation("catalog");
        Relation items = new Relation("items");
        Primitive idPrim = new Primitive("id");
        items.addChild(idPrim);
        catalog.addChild(items);

        PrimitiveLayout idLayout = new PrimitiveLayout(idPrim, TestLayouts.mockPrimitiveStyle(7.0));
        RelationLayout itemsLayout = new RelationLayout(items, TestLayouts.mockRelationStyle());
        RelationLayout catalogLayout = new RelationLayout(catalog, TestLayouts.mockRelationStyle());

        Style model = mock(Style.class);
        when(model.layout(any(com.chiralbehaviors.layout.schema.SchemaNode.class))).thenAnswer(inv -> {
            var n = inv.getArgument(0);
            if (n == idPrim) return idLayout;
            if (n == items) return itemsLayout;
            return catalogLayout;
        });

        catalogLayout.buildPaths(new SchemaPath("catalog"), model);

        assertEquals(new SchemaPath("catalog"), catalogLayout.getSchemaPath());
        assertEquals(new SchemaPath("catalog").child("items"), itemsLayout.getSchemaPath());
        assertEquals(new SchemaPath("catalog").child("items").child("id"), idLayout.getSchemaPath());
    }

    @Test
    void primitiveLayoutHasPathAfterBuildPaths() {
        Primitive prim = new Primitive("score");
        PrimitiveLayout layout = new PrimitiveLayout(prim, TestLayouts.mockPrimitiveStyle(7.0));

        assertNull(layout.getSchemaPath(), "SchemaPath must be null before buildPaths()");

        Style model = mock(Style.class);
        layout.buildPaths(new SchemaPath("score"), model);

        assertNotNull(layout.getSchemaPath());
        assertEquals(new SchemaPath("score"), layout.getSchemaPath());
    }

    @Test
    void buildPathsIsIdempotentWithRewire() {
        Relation schema = new Relation("root");
        schema.addChild(new Primitive("value"));

        PrimitiveLayout valueLayout = new PrimitiveLayout((Primitive) schema.getChildren().get(0),
                                                           TestLayouts.mockPrimitiveStyle(7.0));
        RelationLayout layout = new RelationLayout(schema, TestLayouts.mockRelationStyle());

        Style model = mock(Style.class);
        when(model.layout(any(com.chiralbehaviors.layout.schema.SchemaNode.class))).thenAnswer(inv -> {
            var n = inv.getArgument(0);
            if (n instanceof Primitive) return valueLayout;
            return layout;
        });

        // First call
        layout.buildPaths(new SchemaPath("root"), model);
        assertEquals(new SchemaPath("root"), layout.getSchemaPath());
        assertEquals(new SchemaPath("root").child("value"), valueLayout.getSchemaPath());

        // Second call with different path - should rewire
        layout.buildPaths(new SchemaPath("newRoot"), model);
        assertEquals(new SchemaPath("newRoot"), layout.getSchemaPath());
        assertEquals(new SchemaPath("newRoot").child("value"), valueLayout.getSchemaPath());
    }

    @Test
    void pathsAvailableBeforeMeasure() {
        // The key invariant: buildPaths() sets paths WITHOUT calling measure()
        Relation schema = new Relation("orders");
        Primitive amountPrim = new Primitive("amount");
        Primitive statusPrim = new Primitive("status");
        schema.addChild(amountPrim);
        schema.addChild(statusPrim);

        PrimitiveLayout amountLayout = new PrimitiveLayout(amountPrim, TestLayouts.mockPrimitiveStyle(7.0));
        PrimitiveLayout statusLayout = new PrimitiveLayout(statusPrim, TestLayouts.mockPrimitiveStyle(7.0));
        RelationLayout relLayout = new RelationLayout(schema, TestLayouts.mockRelationStyle());

        Style model = mock(Style.class);
        when(model.layout(any(com.chiralbehaviors.layout.schema.SchemaNode.class))).thenAnswer(inv -> {
            var n = inv.getArgument(0);
            if (n == amountPrim) return amountLayout;
            if (n == statusPrim) return statusLayout;
            return relLayout;
        });

        // Build paths WITHOUT calling measure()
        relLayout.buildPaths(new SchemaPath("orders"), model);

        // Verify paths are set before any measure() call
        assertNotNull(relLayout.getSchemaPath());
        assertNotNull(amountLayout.getSchemaPath());
        assertNotNull(statusLayout.getSchemaPath());

        // Verify correctness
        assertEquals("orders", relLayout.getSchemaPath().leaf());
        assertEquals("amount", amountLayout.getSchemaPath().leaf());
        assertEquals("status", statusLayout.getSchemaPath().leaf());

        // Verify MeasureResult is NOT yet set (no measure called)
        assertNull(relLayout.getMeasureResult(), "measure() was not called, so MeasureResult should be null");
        assertNull(amountLayout.getMeasureResult(), "measure() was not called, so MeasureResult should be null");
    }
}

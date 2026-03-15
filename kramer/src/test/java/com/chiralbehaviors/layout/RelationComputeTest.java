// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for B2: RelationLayout compute* result-returning methods.
 */
class RelationComputeTest {

    private Style mockStyleModel(Relation schema) {
        Style model = mock(Style.class);
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        for (SchemaNode child : schema.getChildren()) {
            if (child instanceof Primitive p) {
                PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
                when(model.layout(p)).thenReturn(pl);
            }
        }
        when(model.layout((SchemaNode) org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            SchemaNode n = inv.getArgument(0);
            if (n instanceof Primitive p) return model.layout(p);
            return model.layout((Relation) n);
        });
        return model;
    }

    @Test
    void computeLayoutCapturesTableDecision() {
        Relation schema = new Relation("catalog");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("code"));

        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        Style model = mockStyleModel(schema);
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        var row = JsonNodeFactory.instance.objectNode();
        row.put("name", "Alice");
        row.put("code", "A1");
        data.add(row);

        layout.measure(data, n -> n, model);

        LayoutResult result = layout.computeLayout(800);
        assertNotNull(result);
        assertEquals(layout.isUseTable(), result.useTable());
    }

    @Test
    void computeCompressCapturesColumnSets() {
        Relation schema = new Relation("parent");
        schema.addChild(new Primitive("f1"));
        schema.addChild(new Primitive("f2"));
        schema.addChild(new Primitive("f3"));

        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        Style model = mockStyleModel(schema);
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        var row = JsonNodeFactory.instance.objectNode();
        row.put("f1", "a");
        row.put("f2", "b");
        row.put("f3", "c");
        data.add(row);

        layout.measure(data, n -> n, model);
        layout.layout(500);

        CompressResult result = layout.computeCompress(500);
        assertNotNull(result);
        assertTrue(result.justifiedWidth() > 0);
    }

    @Test
    void computeCellHeightCapturesHeight() {
        Relation schema = new Relation("parent");
        schema.addChild(new Primitive("f1"));

        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        Style model = mockStyleModel(schema);
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        var row = JsonNodeFactory.instance.objectNode();
        row.put("f1", "value");
        data.add(row);

        layout.measure(data, n -> n, model);
        layout.layout(500);
        layout.compress(500);

        HeightResult result = layout.computeCellHeight(1, 500);
        assertNotNull(result);
        assertTrue(result.height() > 0);
    }
}

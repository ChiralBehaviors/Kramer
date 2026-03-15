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

/**
 * Tests for A4: RelationLayout MeasureResult dual-write.
 * Uses a mock Style to avoid JavaFX Scene creation in CI.
 */
class RelationMeasureResultTest {

    private Style mockStyleModel(Relation schema) {
        Style model = mock(Style.class);
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        for (SchemaNode child : schema.getChildren()) {
            if (child instanceof Primitive p) {
                PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
                when(model.layout(p)).thenReturn(pl);
            }
        }
        // Delegate layout(SchemaNode) to the typed methods
        when(model.layout(any(SchemaNode.class))).thenAnswer(inv -> {
            SchemaNode n = inv.getArgument(0);
            if (n instanceof Primitive p) return model.layout(p);
            return model.layout((Relation) n);
        });
        return model;
    }

    @Test
    void measureResultPopulatedAfterMeasure() {
        Relation schema = new Relation("catalog");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("code"));

        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        Style model = mockStyleModel(schema);
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        var row1 = JsonNodeFactory.instance.objectNode();
        row1.put("name", "Alice");
        row1.put("code", "A1");
        data.add(row1);
        var row2 = JsonNodeFactory.instance.objectNode();
        row2.put("name", "Bob");
        row2.put("code", "B2");
        data.add(row2);

        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result, "MeasureResult should be populated after measure()");

        assertEquals(layout.getLabelWidth(), result.labelWidth());
        assertEquals(layout.columnWidth, result.columnWidth());
        assertEquals(layout.averageChildCardinality, result.averageChildCardinality());
        assertEquals(layout.maxCardinality, result.maxCardinality());
    }

    @Test
    void childResultsMatchChildren() {
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

        MeasureResult result = layout.getMeasureResult();
        assertEquals(2, result.childResults().size(),
                     "childResults should match children count");

        for (int i = 0; i < layout.children.size(); i++) {
            SchemaNodeLayout child = layout.children.get(i);
            MeasureResult childResult = result.childResults().get(i);
            assertNotNull(childResult);
            assertEquals(child.getLabelWidth(), childResult.labelWidth());
        }
    }

    @Test
    void measureResultSurvivesLayoutClear() {
        Relation schema = new Relation("catalog");
        schema.addChild(new Primitive("name"));

        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        Style model = mockStyleModel(schema);
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        var row = JsonNodeFactory.instance.objectNode();
        row.put("name", "Alice");
        data.add(row);

        layout.measure(data, n -> n, model);
        MeasureResult before = layout.getMeasureResult();

        layout.layout(500);

        MeasureResult after = layout.getMeasureResult();
        assertSame(before, after,
                   "MeasureResult must survive layout()/clear()");
    }

    @Test
    void getMeasureResultNullBeforeMeasure() {
        Relation schema = new Relation("catalog");
        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        RelationLayout layout = new RelationLayout(schema, relStyle);

        assertNull(layout.getMeasureResult(),
                   "MeasureResult should be null before measure()");
    }
}

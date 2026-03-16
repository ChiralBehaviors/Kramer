// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

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
 * Tests for PivotStats record and pivot collection in RelationLayout.measure().
 */
class PivotStatsTest {

    // --- PivotStats record construction ---

    @Test
    void pivotStatsRecordConstruction() {
        PivotStats stats = new PivotStats(List.of("A", "B", "C"), 3);
        assertEquals(List.of("A", "B", "C"), stats.pivotValues());
        assertEquals(3, stats.pivotCount());
    }

    @Test
    void pivotValuesDefensivelyCopied() {
        List<String> mutable = new ArrayList<>(List.of("X", "Y"));
        PivotStats stats = new PivotStats(mutable, 2);
        mutable.add("Z");
        assertEquals(2, stats.pivotValues().size(),
                     "pivotValues should be immutably copied at construction");
        assertThrows(UnsupportedOperationException.class,
                     () -> stats.pivotValues().add("W"),
                     "pivotValues list must be unmodifiable");
    }

    @Test
    void pivotCountMatchesPivotValuesSize() {
        List<String> values = List.of("cat", "dog", "bird");
        PivotStats stats = new PivotStats(values, values.size());
        assertEquals(stats.pivotValues().size(), stats.pivotCount());
    }

    @Test
    void measureResultWithNonNullPivotStats() {
        PivotStats ps = new PivotStats(List.of("alpha", "beta"), 2);
        MeasureResult result = new MeasureResult(
            10.0, 20.0, 15.0, 25.0,
            1, false,
            0, 0,
            null, List.of(),
            null, null, ps, null
        );
        assertNotNull(result.pivotStats());
        assertEquals(2, result.pivotStats().pivotCount());
        assertEquals(List.of("alpha", "beta"), result.pivotStats().pivotValues());
    }

    @Test
    void measureResultWithNullPivotStats() {
        MeasureResult result = new MeasureResult(
            10.0, 20.0, 15.0, 25.0,
            1, false,
            0, 0,
            null, List.of(),
            null, null, null, null
        );
        assertNull(result.pivotStats());
    }

    // --- pivot-field configured → non-null pivotStats with distinct values ---

    @Test
    void pivotFieldConfiguredExtractsDistinctValues() {
        Relation schema = new Relation("orders");
        Primitive status = new Primitive("status");
        Primitive amount = new Primitive("amount");
        schema.addChild(status);
        schema.addChild(amount);

        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        SchemaPath rootPath = new SchemaPath("orders");
        LayoutStylesheet stylesheet = stylesheetWithPivot("status");
        Style model = mockStyleModel(schema, stylesheet);

        RelationLayout layout = new RelationLayout(schema, relStyle);
        layout.setSchemaPath(rootPath);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (String s : new String[]{"open", "closed", "open", "pending", "closed", "open"}) {
            var row = JsonNodeFactory.instance.objectNode();
            row.put("status", s);
            row.put("amount", 100);
            data.add(row);
        }

        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result.pivotStats(),
                     "pivotStats must be non-null when pivot-field is configured");
        List<String> values = result.pivotStats().pivotValues();
        assertEquals(3, values.size(), "should have 3 distinct pivot values");
        assertTrue(values.containsAll(List.of("open", "closed", "pending")));
        assertEquals(3, result.pivotStats().pivotCount());
    }

    @Test
    void noPivotFieldConfiguredYieldsNullPivotStats() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));

        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        SchemaPath rootPath = new SchemaPath("items");
        LayoutStylesheet stylesheet = stylesheetNoPivot();
        Style model = mockStyleModel(schema, stylesheet);

        RelationLayout layout = new RelationLayout(schema, relStyle);
        layout.setSchemaPath(rootPath);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        var row = JsonNodeFactory.instance.objectNode();
        row.put("name", "widget");
        data.add(row);

        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNull(result.pivotStats(),
                   "pivotStats must be null when no pivot-field is configured");
    }

    // --- helpers ---

    private LayoutStylesheet stylesheetWithPivot(String pivotField) {
        return new DefaultLayoutStylesheet(null) {
            @Override
            public String getString(SchemaPath path, String property, String defaultValue) {
                if ("pivot-field".equals(property)) return pivotField;
                return defaultValue;
            }

            @Override
            public long getVersion() { return 0; }
        };
    }

    private LayoutStylesheet stylesheetNoPivot() {
        return new DefaultLayoutStylesheet(null) {
            @Override
            public String getString(SchemaPath path, String property, String defaultValue) {
                return defaultValue;
            }

            @Override
            public long getVersion() { return 0; }
        };
    }

    private Style mockStyleModel(Relation schema, LayoutStylesheet stylesheet) {
        Style model = mock(Style.class);
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        for (SchemaNode child : schema.getChildren()) {
            if (child instanceof Primitive p) {
                PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
                when(model.layout(p)).thenReturn(pl);
            }
        }
        when(model.layout(any(SchemaNode.class))).thenAnswer(inv -> {
            SchemaNode n = inv.getArgument(0);
            if (n instanceof Primitive p) return model.layout(p);
            return model.layout((Relation) n);
        });
        when(model.getStylesheet()).thenReturn(stylesheet);
        return model;
    }
}

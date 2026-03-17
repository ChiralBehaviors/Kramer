/**
 * Copyright (c) 2016 Chiral Behaviors, LLC, all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chiralbehaviors.layout.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.ConfiguredMeasurementStrategy;
import com.chiralbehaviors.layout.LayoutPropertyKeys;
import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.TestLayouts;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Integration tests: LayoutQueryState wired into the layout pipeline
 * (RDR-026 Phase 3). Headless — no JavaFX required.
 *
 * @author hhildebrand
 */
class LayoutQueryStateIntegrationTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    // --- Helpers ---

    private Style mockModelWithQueryState(Relation schema, LayoutQueryState queryState) {
        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(queryState);
        when(model.getExpressionEvaluator())
            .thenReturn(new com.chiralbehaviors.layout.expression.ExpressionEvaluator());

        var primStyle = TestLayouts.mockPrimitiveStyle(7.0);
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
        return model;
    }

    private ArrayNode buildData(String[][] rows, String... fields) {
        ArrayNode data = NF.arrayNode();
        for (String[] row : rows) {
            ObjectNode obj = NF.objectNode();
            for (int i = 0; i < fields.length && i < row.length; i++) {
                try {
                    obj.put(fields[i], Double.parseDouble(row[i]));
                } catch (NumberFormatException e) {
                    obj.put(fields[i], row[i]);
                }
            }
            data.add(obj);
        }
        return data;
    }

    private List<String> fieldValues(RelationLayout layout, ArrayNode data,
                                     String field) {
        ObjectNode parent = NF.objectNode();
        parent.set(layout.getNode().getField(), data);
        JsonNode extracted = layout.extractFrom(parent);
        var values = new ArrayList<String>();
        extracted.forEach(row -> {
            JsonNode val = row.get(field);
            values.add(val != null ? val.asText() : "null");
        });
        return values;
    }

    // --- LayoutQueryState drives filter via pipeline ---

    @Test
    void queryStateFilterDrivesLayoutPipeline() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("revenue"));

        var style = new Style(new ConfiguredMeasurementStrategy());
        var queryState = new LayoutQueryState(style);
        SchemaPath rootPath = new SchemaPath("items");

        queryState.setFilterExpression(rootPath, "$revenue > 50");

        var layout = new RelationLayout(schema, TestLayouts.mockRelationStyle());
        layout.setSchemaPath(rootPath);
        var model = mockModelWithQueryState(schema, queryState);

        ArrayNode data = buildData(new String[][] {
            { "Alice", "100" },
            { "Bob",   "30"  },
            { "Carol", "75"  }
        }, "name", "revenue");

        layout.measure(data, n -> n, model);

        var names = fieldValues(layout, data, "name");
        assertEquals(List.of("Alice", "Carol"), names);
    }

    // --- LayoutQueryState drives sort via pipeline ---

    @Test
    void queryStateSortDrivesLayoutPipeline() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("score"));

        var style = new Style(new ConfiguredMeasurementStrategy());
        var queryState = new LayoutQueryState(style);
        SchemaPath rootPath = new SchemaPath("items");

        queryState.setSortExpression(rootPath, "$score");

        var layout = new RelationLayout(schema, TestLayouts.mockRelationStyle());
        layout.setSchemaPath(rootPath);
        var model = mockModelWithQueryState(schema, queryState);

        ArrayNode data = buildData(new String[][] {
            { "Charlie", "30" },
            { "Alice",   "10" },
            { "Bob",     "20" }
        }, "name", "score");

        layout.measure(data, n -> n, model);

        var names = fieldValues(layout, data, "name");
        assertEquals(List.of("Alice", "Bob", "Charlie"), names);
    }

    // --- LayoutQueryState visible=false hides field ---

    @Test
    void queryStateVisibleFalseHidesField() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("hidden_field"));
        schema.addChild(new Primitive("score"));

        var style = new Style(new ConfiguredMeasurementStrategy());
        var queryState = new LayoutQueryState(style);
        SchemaPath rootPath = new SchemaPath("items");
        SchemaPath hiddenPath = rootPath.child("hidden_field");

        queryState.setVisible(hiddenPath, false);

        var layout = new RelationLayout(schema, TestLayouts.mockRelationStyle());
        layout.setSchemaPath(rootPath);
        var model = mockModelWithQueryState(schema, queryState);

        ArrayNode data = buildData(new String[][] {
            { "Alice", "secret", "100" }
        }, "name", "hidden_field", "score");

        layout.measure(data, n -> n, model);

        // hidden_field should be excluded from children
        var childFields = layout.getChildren().stream()
            .map(c -> c.getField())
            .toList();
        assertFalse(childFields.contains("hidden_field"),
            "hidden_field should be excluded from layout children");
        assertTrue(childFields.contains("name"));
        assertTrue(childFields.contains("score"));
    }

    // --- Change listener fires on mutation ---

    @Test
    void changeListenerIntegration() {
        var style = new Style(new ConfiguredMeasurementStrategy());
        var queryState = new LayoutQueryState(style);
        var relayoutCount = new AtomicInteger(0);

        // Simulate what explorer would do
        queryState.addChangeListener(relayoutCount::incrementAndGet);

        queryState.setFilterExpression(new SchemaPath("items"), "$x > 5");
        assertEquals(1, relayoutCount.get());

        queryState.setSortExpression(new SchemaPath("items"), "$score");
        assertEquals(2, relayoutCount.get());

        queryState.reset();
        assertEquals(3, relayoutCount.get());
    }

    // --- Version increments are visible to ExpressionEvaluator ---

    @Test
    void versionIncrementVisibleThroughLayoutStylesheet() {
        var style = new Style(new ConfiguredMeasurementStrategy());
        var queryState = new LayoutQueryState(style);

        long v0 = queryState.getVersion();
        queryState.setFilterExpression(new SchemaPath("items"), "$x > 5");
        long v1 = queryState.getVersion();

        assertTrue(v1 > v0, "Version must increment so ExpressionEvaluator cache invalidates");
    }

    // --- PIVOT_FIELD constant is accessible ---

    @Test
    void pivotFieldConstantExists() {
        assertEquals("pivot-field", LayoutPropertyKeys.PIVOT_FIELD);
    }
}

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

package com.chiralbehaviors.layout.expression;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.DefaultLayoutStylesheet;
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
 * Integration tests for expression pipeline wiring in RelationLayout.measure()
 * (RDR-021 Step C).
 *
 * @author hhildebrand
 */
class ExpressionPipelineTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    // --- Helpers ---

    private Style mockModel(Relation schema, DefaultLayoutStylesheet stylesheet) {
        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(stylesheet);
        when(model.getExpressionEvaluator()).thenReturn(new ExpressionEvaluator());

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

    private RelationLayout buildLayout(Relation schema) {
        return new RelationLayout(schema, TestLayouts.mockRelationStyle());
    }

    private ArrayNode buildData(String[][] rows, String... fields) {
        ArrayNode data = NF.arrayNode();
        for (String[] row : rows) {
            ObjectNode obj = NF.objectNode();
            for (int i = 0; i < fields.length && i < row.length; i++) {
                // Try to parse as number
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

    // --- filter-expression tests ---

    @Test
    void filterExpressionExcludesRows() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("revenue"));

        var stylesheet = new DefaultLayoutStylesheet(new Style(
            new com.chiralbehaviors.layout.ConfiguredMeasurementStrategy()));
        SchemaPath rootPath = new SchemaPath("items");
        stylesheet.setOverride(rootPath, LayoutPropertyKeys.FILTER_EXPRESSION,
            "$revenue > 50");

        var layout = buildLayout(schema);
        layout.setSchemaPath(rootPath);
        var model = mockModel(schema, stylesheet);

        ArrayNode data = buildData(new String[][] {
            { "Alice", "100" },
            { "Bob",   "30"  },
            { "Carol", "75"  },
            { "Dave",  "10"  }
        }, "name", "revenue");

        layout.measure(data, n -> n, model);

        // Only Alice (100) and Carol (75) pass the filter
        var names = fieldValues(layout, data, "name");
        assertEquals(List.of("Alice", "Carol"), names);
    }

    @Test
    void filterExpressionInvalidExpressionIgnored() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));

        var stylesheet = new DefaultLayoutStylesheet(new Style(
            new com.chiralbehaviors.layout.ConfiguredMeasurementStrategy()));
        SchemaPath rootPath = new SchemaPath("items");
        stylesheet.setOverride(rootPath, LayoutPropertyKeys.FILTER_EXPRESSION,
            "!!! invalid !!!");

        var layout = buildLayout(schema);
        layout.setSchemaPath(rootPath);
        var model = mockModel(schema, stylesheet);

        ArrayNode data = buildData(new String[][] {
            { "Alice" }, { "Bob" }
        }, "name");

        // Should not crash; invalid expression is treated as absent
        layout.measure(data, n -> n, model);
        var names = fieldValues(layout, data, "name");
        assertEquals(List.of("Alice", "Bob"), names);
    }

    @Test
    void filterExpressionWithNullField() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("status"));

        var stylesheet = new DefaultLayoutStylesheet(new Style(
            new com.chiralbehaviors.layout.ConfiguredMeasurementStrategy()));
        SchemaPath rootPath = new SchemaPath("items");
        stylesheet.setOverride(rootPath, LayoutPropertyKeys.FILTER_EXPRESSION,
            "$status != null");

        var layout = buildLayout(schema);
        layout.setSchemaPath(rootPath);
        var model = mockModel(schema, stylesheet);

        ArrayNode data = NF.arrayNode();
        var r1 = NF.objectNode(); r1.put("name", "Alice"); r1.put("status", "active"); data.add(r1);
        var r2 = NF.objectNode(); r2.put("name", "Bob"); r2.putNull("status"); data.add(r2);
        var r3 = NF.objectNode(); r3.put("name", "Carol"); r3.put("status", "inactive"); data.add(r3);

        layout.measure(data, n -> n, model);
        var names = fieldValues(layout, data, "name");
        assertEquals(List.of("Alice", "Carol"), names);
    }

    // --- formula-expression tests ---

    @Test
    void formulaExpressionOverlaysComputedValue() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("price"));
        schema.addChild(new Primitive("qty"));
        schema.addChild(new Primitive("total"));

        var stylesheet = new DefaultLayoutStylesheet(new Style(
            new com.chiralbehaviors.layout.ConfiguredMeasurementStrategy()));
        SchemaPath rootPath = new SchemaPath("items");
        SchemaPath totalPath = rootPath.child("total");
        stylesheet.setOverride(totalPath, LayoutPropertyKeys.FORMULA_EXPRESSION,
            "$price * $qty");

        var layout = buildLayout(schema);
        layout.setSchemaPath(rootPath);
        var model = mockModel(schema, stylesheet);

        ArrayNode data = buildData(new String[][] {
            { "10", "3", "0" },
            { "20", "2", "0" }
        }, "price", "qty", "total");

        layout.measure(data, n -> n, model);

        var totals = fieldValues(layout, data, "total");
        assertEquals(List.of("30.0", "40.0"), totals);
    }

    @Test
    void formulaDependencyOrder() {
        // "subtotal" = $price * $qty, "tax" = $subtotal * 0.1
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("price"));
        schema.addChild(new Primitive("qty"));
        schema.addChild(new Primitive("subtotal"));
        schema.addChild(new Primitive("tax"));

        var stylesheet = new DefaultLayoutStylesheet(new Style(
            new com.chiralbehaviors.layout.ConfiguredMeasurementStrategy()));
        SchemaPath rootPath = new SchemaPath("items");
        stylesheet.setOverride(rootPath.child("subtotal"),
            LayoutPropertyKeys.FORMULA_EXPRESSION, "$price * $qty");
        stylesheet.setOverride(rootPath.child("tax"),
            LayoutPropertyKeys.FORMULA_EXPRESSION, "$subtotal * 0.1");

        var layout = buildLayout(schema);
        layout.setSchemaPath(rootPath);
        var model = mockModel(schema, stylesheet);

        ArrayNode data = buildData(new String[][] {
            { "100", "2", "0", "0" }
        }, "price", "qty", "subtotal", "tax");

        layout.measure(data, n -> n, model);

        var subtotals = fieldValues(layout, data, "subtotal");
        var taxes = fieldValues(layout, data, "tax");
        assertEquals(List.of("200.0"), subtotals);
        assertEquals(List.of("20.0"), taxes);
    }

    // --- sort-expression tests ---

    @Test
    void sortExpressionOverridesSortFields() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("score"));
        schema.setSortFields(List.of("name")); // alphabetical by name

        var stylesheet = new DefaultLayoutStylesheet(new Style(
            new com.chiralbehaviors.layout.ConfiguredMeasurementStrategy()));
        SchemaPath rootPath = new SchemaPath("items");
        stylesheet.setOverride(rootPath, LayoutPropertyKeys.SORT_EXPRESSION,
            "$score"); // sort by score instead

        var layout = buildLayout(schema);
        layout.setSchemaPath(rootPath);
        var model = mockModel(schema, stylesheet);

        ArrayNode data = buildData(new String[][] {
            { "Charlie", "30" },
            { "Alice",   "10" },
            { "Bob",     "20" }
        }, "name", "score");

        layout.measure(data, n -> n, model);

        var names = fieldValues(layout, data, "name");
        // Sorted by score: Alice(10), Bob(20), Charlie(30)
        assertEquals(List.of("Alice", "Bob", "Charlie"), names);
    }

    @Test
    void sortExpressionWithFunction() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("delta"));

        var stylesheet = new DefaultLayoutStylesheet(new Style(
            new com.chiralbehaviors.layout.ConfiguredMeasurementStrategy()));
        SchemaPath rootPath = new SchemaPath("items");
        stylesheet.setOverride(rootPath, LayoutPropertyKeys.SORT_EXPRESSION,
            "abs($delta)");

        var layout = buildLayout(schema);
        layout.setSchemaPath(rootPath);
        var model = mockModel(schema, stylesheet);

        ArrayNode data = buildData(new String[][] {
            { "A", "-30" },
            { "B", "5"   },
            { "C", "-10" }
        }, "name", "delta");

        layout.measure(data, n -> n, model);

        var names = fieldValues(layout, data, "name");
        // Sorted by abs(delta): B(5), C(10), A(30)
        assertEquals(List.of("B", "C", "A"), names);
    }

    // --- Pipeline ordering: filter before formula before sort ---

    @Test
    void pipelineFilterThenFormulaThenSort() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("price"));
        schema.addChild(new Primitive("qty"));
        schema.addChild(new Primitive("total"));

        var stylesheet = new DefaultLayoutStylesheet(new Style(
            new com.chiralbehaviors.layout.ConfiguredMeasurementStrategy()));
        SchemaPath rootPath = new SchemaPath("items");
        // Filter: only rows where price > 5
        stylesheet.setOverride(rootPath, LayoutPropertyKeys.FILTER_EXPRESSION,
            "$price > 5");
        // Formula: total = price * qty
        stylesheet.setOverride(rootPath.child("total"),
            LayoutPropertyKeys.FORMULA_EXPRESSION, "$price * $qty");
        // Sort by total
        stylesheet.setOverride(rootPath, LayoutPropertyKeys.SORT_EXPRESSION,
            "$total");

        var layout = buildLayout(schema);
        layout.setSchemaPath(rootPath);
        var model = mockModel(schema, stylesheet);

        ArrayNode data = buildData(new String[][] {
            { "A", "3",  "10", "0" },  // filtered out (price <= 5)
            { "B", "20", "1",  "0" },  // total=20
            { "C", "10", "5",  "0" },  // total=50
            { "D", "15", "2",  "0" }   // total=30
        }, "name", "price", "qty", "total");

        layout.measure(data, n -> n, model);

        // A filtered out. Remaining sorted by total: B(20), D(30), C(50)
        var names = fieldValues(layout, data, "name");
        assertEquals(List.of("B", "D", "C"), names);

        var totals = fieldValues(layout, data, "total");
        assertEquals(List.of("20.0", "30.0", "50.0"), totals);
    }

    // --- aggregate-expression ---

    @Test
    void aggregateExpressionComputesValue() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("revenue"));

        var stylesheet = new DefaultLayoutStylesheet(new Style(
            new com.chiralbehaviors.layout.ConfiguredMeasurementStrategy()));
        SchemaPath rootPath = new SchemaPath("items");
        stylesheet.setOverride(rootPath.child("revenue"),
            LayoutPropertyKeys.AGGREGATE_EXPRESSION, "sum($revenue)");

        var layout = buildLayout(schema);
        layout.setSchemaPath(rootPath);
        var model = mockModel(schema, stylesheet);

        ArrayNode data = buildData(new String[][] {
            { "A", "100" },
            { "B", "200" },
            { "C", "300" }
        }, "name", "revenue");

        layout.measure(data, n -> n, model);

        // Aggregate results stored on layout
        var aggregates = layout.getAggregateResults();
        assertNotNull(aggregates);
        assertTrue(aggregates.containsKey("revenue"));
        assertEquals(600.0, aggregates.get("revenue"));
    }

    // --- Expression cache invalidation ---

    @Test
    void expressionCacheInvalidatesOnStylesheetVersionChange() {
        var evaluator = new ExpressionEvaluator();
        evaluator.syncVersion(5);
        var ast1 = assertDoesNotThrow(() -> evaluator.compile("$a + $b"));

        // Same version — should be cached
        evaluator.syncVersion(5);
        var ast2 = assertDoesNotThrow(() -> evaluator.compile("$a + $b"));
        assertSame(ast1, ast2);

        // New version — cache cleared
        evaluator.syncVersion(6);
        var ast3 = assertDoesNotThrow(() -> evaluator.compile("$a + $b"));
        assertNotSame(ast1, ast3);
    }
}

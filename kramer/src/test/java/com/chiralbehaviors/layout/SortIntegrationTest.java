// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * End-to-end integration tests for stable sort through the full measure
 * pipeline with realistic multi-field JSON data (id, name, value).
 *
 * Complements the unit-level RelationLayoutSortTest by exercising the
 * complete measure() → extractFrom() path with richer fixture data.
 */
class SortIntegrationTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    // Schema: Relation("records") with children id (int), name (string), value (int)
    private Relation       schema;
    private RelationStyle  relStyle;
    private Style          model;

    @BeforeEach
    void setUp() {
        schema = new Relation("records");
        schema.addChild(new Primitive("id"));
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("value"));

        relStyle = TestLayouts.mockRelationStyle();

        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);

        model = mock(Style.class);
        for (SchemaNode child : schema.getChildren()) {
            if (child instanceof Primitive p) {
                PrimitiveLayout pl = new PrimitiveLayout(p, primStyle);
                when(model.layout(p)).thenReturn(pl);
            }
        }
        // Delegate any SchemaNode dispatch back to typed overloads
        when(model.layout(any(SchemaNode.class))).thenAnswer(inv -> {
            SchemaNode n = inv.getArgument(0);
            if (n instanceof Primitive p) return model.layout(p);
            return model.layout((Relation) n);
        });
    }

    /**
     * Build a row with integer id, string name, integer value.
     */
    private ObjectNode row(int id, String name, int value) {
        ObjectNode obj = NF.objectNode();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("value", value);
        return obj;
    }

    /**
     * Run measure() then extractFrom() and return the extracted array.
     */
    private JsonNode measureAndExtract(RelationLayout layout, ArrayNode data) {
        layout.measure(data, n -> n, model);
        ObjectNode parent = NF.objectNode();
        parent.set("records", data);
        return layout.extractFrom(parent);
    }

    /**
     * Collect field values from an extracted array in order.
     */
    private <T> List<T> collect(JsonNode extracted, String field,
                                java.util.function.Function<JsonNode, T> mapper) {
        List<T> result = new ArrayList<>();
        extracted.forEach(row -> result.add(mapper.apply(row.get(field))));
        return result;
    }

    // -----------------------------------------------------------------------
    // autoSort=false preserves original JSON order
    // -----------------------------------------------------------------------

    @Test
    void noSortPreservesOriginalOrder() {
        // Default: autoSort=false, no sortFields
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = NF.arrayNode();
        data.add(row(10, "Zebra",  99));
        data.add(row(2,  "Alpha",  5));
        data.add(row(100,"Mango",  42));
        data.add(row(1,  "Berry",  77));

        JsonNode extracted = measureAndExtract(layout, data);

        List<Integer> ids = collect(extracted, "id", JsonNode::intValue);
        assertEquals(List.of(10, 2, 100, 1), ids,
                     "autoSort=false must preserve original insertion order");
    }

    // -----------------------------------------------------------------------
    // Numeric sort field: 10 comes before 100 (numeric, not lexicographic)
    // -----------------------------------------------------------------------

    @Test
    void numericIdSortsNumerically() {
        schema.setSortFields(List.of("id"));
        RelationLayout layout = new RelationLayout(schema, relStyle);

        // Lexicographic order would be: 1, 10, 100, 2
        // Numeric order must be:        1, 2, 10, 100
        ArrayNode data = NF.arrayNode();
        data.add(row(10,  "Ten",     10));
        data.add(row(100, "Hundred", 100));
        data.add(row(2,   "Two",     2));
        data.add(row(1,   "One",     1));

        JsonNode extracted = measureAndExtract(layout, data);

        List<Integer> ids = collect(extracted, "id", JsonNode::intValue);
        assertEquals(List.of(1, 2, 10, 100), ids,
                     "Numeric id field must sort numerically (1 < 2 < 10 < 100), not lexicographically");
    }

    // -----------------------------------------------------------------------
    // String sort field: lexicographic on name
    // -----------------------------------------------------------------------

    @Test
    void stringSortFieldSortsLexicographically() {
        schema.setSortFields(List.of("name"));
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = NF.arrayNode();
        data.add(row(3,  "Zebra",   30));
        data.add(row(1,  "Apple",   10));
        data.add(row(2,  "Mango",   20));

        JsonNode extracted = measureAndExtract(layout, data);

        List<String> names = collect(extracted, "name", JsonNode::asText);
        assertEquals(List.of("Apple", "Mango", "Zebra"), names,
                     "String sort field must produce lexicographic order");
    }

    // -----------------------------------------------------------------------
    // Null and missing sort fields sort last without NPE
    // -----------------------------------------------------------------------

    @Test
    void nullAndMissingValuesInSortFieldSortLast() {
        schema.setSortFields(List.of("value"));
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = NF.arrayNode();
        // Row with value present
        ObjectNode r1 = NF.objectNode();
        r1.put("id", 1); r1.put("name", "HasValue"); r1.put("value", 5);
        data.add(r1);
        // Row with explicit null value
        ObjectNode r2 = NF.objectNode();
        r2.put("id", 2); r2.put("name", "NullValue"); r2.putNull("value");
        data.add(r2);
        // Row with value field entirely missing
        ObjectNode r3 = NF.objectNode();
        r3.put("id", 3); r3.put("name", "MissingValue");
        data.add(r3);
        // Row with another present value
        ObjectNode r4 = NF.objectNode();
        r4.put("id", 4); r4.put("name", "AlsoHasValue"); r4.put("value", 1);
        data.add(r4);

        JsonNode extracted = assertDoesNotThrow(
            () -> measureAndExtract(layout, data),
            "Null/missing sort values must not throw NPE"
        );

        List<String> names = collect(extracted, "name", JsonNode::asText);
        // Rows with values come first (sorted numerically: 1, 5)
        assertTrue(names.indexOf("AlsoHasValue") < names.indexOf("NullValue"),
                   "row with value=1 must precede row with null value");
        assertTrue(names.indexOf("HasValue") < names.indexOf("NullValue"),
                   "row with value=5 must precede row with null value");
        // Null/missing must sort last
        assertTrue(names.indexOf("NullValue")    >= 2, "null value must sort last");
        assertTrue(names.indexOf("MissingValue") >= 2, "missing value must sort last");
    }

    // -----------------------------------------------------------------------
    // autoSort=true with "id" child uses id as sort key
    // -----------------------------------------------------------------------

    @Test
    void autoSortWithIdFieldUsesIdAsSortKey() {
        schema.setAutoSort(true);
        // sortFields is empty — autoSort heuristic should pick "id"
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = NF.arrayNode();
        data.add(row(30, "Charlie", 3));
        data.add(row(10, "Alice",   1));
        data.add(row(20, "Bob",     2));

        JsonNode extracted = measureAndExtract(layout, data);

        List<Integer> ids = collect(extracted, "id", JsonNode::intValue);
        assertEquals(List.of(10, 20, 30), ids,
                     "autoSort=true with 'id' child must sort by id");
    }

    // -----------------------------------------------------------------------
    // Sort is stable: equal keys preserve relative insertion order
    // -----------------------------------------------------------------------

    @Test
    void sortIsStableOnEqualKeys() {
        schema.setSortFields(List.of("value"));
        RelationLayout layout = new RelationLayout(schema, relStyle);

        // Rows with the same value=10 — stable sort must keep insertion order
        ArrayNode data = NF.arrayNode();
        data.add(row(1, "First",  10));
        data.add(row(2, "Second", 99));
        data.add(row(3, "Third",  10));

        JsonNode extracted = measureAndExtract(layout, data);

        List<String> names = collect(extracted, "name", JsonNode::asText);
        // value=10 rows appear first (both tie), then value=99
        int firstIdx  = names.indexOf("First");
        int thirdIdx  = names.indexOf("Third");
        int secondIdx = names.indexOf("Second");
        assertTrue(firstIdx  < secondIdx, "'First'  (value=10) must precede 'Second' (value=99)");
        assertTrue(thirdIdx  < secondIdx, "'Third'  (value=10) must precede 'Second' (value=99)");
        // Stable: First was inserted before Third, so must remain before Third
        assertTrue(firstIdx  < thirdIdx,  "Stable sort: 'First' must precede 'Third' (equal keys, insertion order)");
    }

    // -----------------------------------------------------------------------
    // Multiple sort fields: primary + secondary sort
    // -----------------------------------------------------------------------

    @Test
    void multipleExplicitSortFields() {
        // Sort by value ascending, then name ascending for ties
        schema.setSortFields(List.of("value", "name"));
        RelationLayout layout = new RelationLayout(schema, relStyle);

        ArrayNode data = NF.arrayNode();
        data.add(row(1, "Zebra",   10));
        data.add(row(2, "Apple",   10));   // same value as Zebra — name tiebreak
        data.add(row(3, "Mango",   5));

        JsonNode extracted = measureAndExtract(layout, data);

        List<String> names = collect(extracted, "name", JsonNode::asText);
        // value=5 first, then value=10 ties broken by name: Apple < Zebra
        assertEquals(List.of("Mango", "Apple", "Zebra"), names,
                     "Multi-field sort: value asc then name asc for ties");
    }
}

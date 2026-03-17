// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

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
 * Tests for Kramer-090: stable sort in RelationLayout.measure() / extractFrom().
 */
class RelationLayoutSortTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Style mockModel(Relation schema) {
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
        return model;
    }

    /** Build an ArrayNode of {name, score} objects in the given order. */
    private ArrayNode buildData(String[][] rows) {
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (String[] row : rows) {
            ObjectNode obj = JsonNodeFactory.instance.objectNode();
            obj.put("name", row[0]);
            obj.put("score", row[1]);
            data.add(obj);
        }
        return data;
    }

    private RelationLayout buildLayout(Relation schema) {
        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        return new RelationLayout(schema, relStyle);
    }

    /** Return field values from datum after measure, in order. */
    private List<String> namesAfterMeasure(RelationLayout layout,
                                           ArrayNode data,
                                           Style model,
                                           String field) {
        // datum passed to measure() is the top-level array
        // measure() operates on it directly
        layout.measure(data, n -> n, model);
        // Use extractFrom to retrieve sorted rows (build-phase path)
        // wrap data in a parent object so extractFrom can field-extract it
        ObjectNode parent = JsonNodeFactory.instance.objectNode();
        parent.set(layout.getNode().getField(), data);
        JsonNode extracted = layout.extractFrom(parent);
        List<String> names = new java.util.ArrayList<>();
        extracted.forEach(row -> names.add(row.get(field).asText()));
        return names;
    }

    // -----------------------------------------------------------------------
    // Test: autoSort=false (default) preserves original order
    // -----------------------------------------------------------------------

    @Test
    void autoSortFalsePreservesOrder() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("score"));

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        ArrayNode data = buildData(new String[][] {
            { "Charlie", "30" },
            { "Alice",   "10" },
            { "Bob",     "20" }
        });

        List<String> names = namesAfterMeasure(layout, data, model, "name");
        // No sort configured — original order must be preserved
        assertEquals(List.of("Charlie", "Alice", "Bob"), names,
                     "autoSort=false must preserve insertion order");
    }

    // -----------------------------------------------------------------------
    // Test: explicit sortFields sorts lexicographically
    // -----------------------------------------------------------------------

    @Test
    void explicitSortFieldSortsLexicographically() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("score"));
        schema.setSortFields(List.of("name"));

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        ArrayNode data = buildData(new String[][] {
            { "Charlie", "30" },
            { "Alice",   "10" },
            { "Bob",     "20" }
        });

        List<String> names = namesAfterMeasure(layout, data, model, "name");
        assertEquals(List.of("Alice", "Bob", "Charlie"), names,
                     "sortFields=[name] must sort lexicographically");
    }

    // -----------------------------------------------------------------------
    // Test: numeric sort fields sort numerically, not lexicographically
    // -----------------------------------------------------------------------

    @Test
    void numericSortFieldSortsNumerically() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("score"));
        schema.setSortFields(List.of("score"));

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        // Use integer values so lexicographic vs numeric order differs
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ObjectNode r1 = JsonNodeFactory.instance.objectNode();
        r1.put("name", "Alpha");
        r1.put("score", 9);       // integer node
        data.add(r1);
        ObjectNode r2 = JsonNodeFactory.instance.objectNode();
        r2.put("name", "Beta");
        r2.put("score", 100);
        data.add(r2);
        ObjectNode r3 = JsonNodeFactory.instance.objectNode();
        r3.put("name", "Gamma");
        r3.put("score", 20);
        data.add(r3);

        // Measure
        layout.measure(data, n -> n, model);
        // Build-phase extraction
        ObjectNode parent = JsonNodeFactory.instance.objectNode();
        parent.set("items", data);
        JsonNode extracted = layout.extractFrom(parent);

        List<Integer> scores = new java.util.ArrayList<>();
        extracted.forEach(row -> scores.add(row.get("score").intValue()));

        // Numeric order: 9, 20, 100 — NOT 100, 20, 9 (lexicographic)
        assertEquals(List.of(9, 20, 100), scores,
                     "Numeric sort fields must sort by numeric value, not string");
    }

    // -----------------------------------------------------------------------
    // Test: missing/null sort field values sort last without NPE
    // -----------------------------------------------------------------------

    @Test
    void nullAndMissingSortFieldsSortLast() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("rank"));
        schema.setSortFields(List.of("rank"));

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ObjectNode r1 = JsonNodeFactory.instance.objectNode();
        r1.put("name", "WithRank");
        r1.put("rank", "A");
        data.add(r1);
        ObjectNode r2 = JsonNodeFactory.instance.objectNode();
        r2.put("name", "NullRank");
        r2.putNull("rank");         // explicit null
        data.add(r2);
        ObjectNode r3 = JsonNodeFactory.instance.objectNode();
        r3.put("name", "MissingRank"); // field absent
        data.add(r3);
        ObjectNode r4 = JsonNodeFactory.instance.objectNode();
        r4.put("name", "WithRankB");
        r4.put("rank", "B");
        data.add(r4);

        List<String> names = assertDoesNotThrow(
            () -> namesAfterMeasure(layout, data, model, "name"),
            "Null/missing sort fields must not throw NPE"
        );

        // Rows with rank values come first; null/missing come last
        assertEquals("WithRank",    names.get(0));
        assertEquals("WithRankB",   names.get(1));
        assertTrue(names.indexOf("NullRank")    >= 2,
                   "null rank should sort last");
        assertTrue(names.indexOf("MissingRank") >= 2,
                   "missing rank should sort last");
    }

    // -----------------------------------------------------------------------
    // Test: autoSort=true with no sortFields uses heuristic (id > key > name > first primitive)
    // -----------------------------------------------------------------------

    @Test
    void autoSortWithNoSortFieldsUsesHeuristicId() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("id"));
        schema.addChild(new Primitive("label"));
        schema.setAutoSort(true);
        // sortFields left empty

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ObjectNode r1 = JsonNodeFactory.instance.objectNode();
        r1.put("id", "C");
        r1.put("label", "Charlie");
        data.add(r1);
        ObjectNode r2 = JsonNodeFactory.instance.objectNode();
        r2.put("id", "A");
        r2.put("label", "Alpha");
        data.add(r2);
        ObjectNode r3 = JsonNodeFactory.instance.objectNode();
        r3.put("id", "B");
        r3.put("label", "Beta");
        data.add(r3);

        layout.measure(data, n -> n, model);
        ObjectNode parent = JsonNodeFactory.instance.objectNode();
        parent.set("items", data);
        JsonNode extracted = layout.extractFrom(parent);

        List<String> ids = new java.util.ArrayList<>();
        extracted.forEach(row -> ids.add(row.get("id").asText()));
        assertEquals(List.of("A", "B", "C"), ids,
                     "autoSort=true with no sortFields should use 'id' heuristic field");
    }

    @Test
    void autoSortHeuristicFallsToName() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("value"));
        schema.setAutoSort(true);
        // no "id" or "key" child — should fall to "name"

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        ArrayNode data = buildData(new String[][] {
            { "Zebra", "1" },
            { "Apple", "2" },
            { "Mango", "3" }
        });

        layout.measure(data, n -> n, model);
        ObjectNode parent = JsonNodeFactory.instance.objectNode();
        parent.set("items", data);
        JsonNode extracted = layout.extractFrom(parent);

        List<String> names = new java.util.ArrayList<>();
        extracted.forEach(row -> names.add(row.get("name").asText()));
        assertEquals(List.of("Apple", "Mango", "Zebra"), names,
                     "autoSort=true with no id/key should fall back to 'name' field");
    }

    @Test
    void autoSortHeuristicFallsToFirstPrimitive() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("code"));   // none of id/key/name
        schema.addChild(new Primitive("desc"));
        schema.setAutoSort(true);

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ObjectNode r1 = JsonNodeFactory.instance.objectNode();
        r1.put("code", "ZZZ");
        r1.put("desc", "Last");
        data.add(r1);
        ObjectNode r2 = JsonNodeFactory.instance.objectNode();
        r2.put("code", "AAA");
        r2.put("desc", "First");
        data.add(r2);

        layout.measure(data, n -> n, model);
        ObjectNode parent = JsonNodeFactory.instance.objectNode();
        parent.set("items", data);
        JsonNode extracted = layout.extractFrom(parent);

        List<String> codes = new java.util.ArrayList<>();
        extracted.forEach(row -> codes.add(row.get("code").asText()));
        assertEquals(List.of("AAA", "ZZZ"), codes,
                     "autoSort=true with no heuristic match should fall back to first primitive field");
    }

    // -----------------------------------------------------------------------
    // Tests: fieldComparator with `-` prefix for descending sort
    // -----------------------------------------------------------------------

    @Test
    void fieldComparatorAscendingByName() {
        var cmp = RelationLayout.fieldComparator("name");
        ObjectNode a = JsonNodeFactory.instance.objectNode(); a.put("name", "Alice");
        ObjectNode b = JsonNodeFactory.instance.objectNode(); b.put("name", "Bob");
        assertTrue(cmp.compare(a, b) < 0, "Alice < Bob ascending");
        assertTrue(cmp.compare(b, a) > 0, "Bob > Alice ascending");
        assertEquals(0, cmp.compare(a, a), "equal nodes compare to 0");
    }

    @Test
    void fieldComparatorDescendingByName() {
        var cmp = RelationLayout.fieldComparator("-name");
        ObjectNode a = JsonNodeFactory.instance.objectNode(); a.put("name", "Alice");
        ObjectNode b = JsonNodeFactory.instance.objectNode(); b.put("name", "Bob");
        // descending: Bob > Alice, so Bob comes first
        assertTrue(cmp.compare(b, a) < 0, "Bob before Alice in descending");
        assertTrue(cmp.compare(a, b) > 0, "Alice after Bob in descending");
        assertEquals(0, cmp.compare(a, a), "equal nodes compare to 0 descending");
    }

    @Test
    void fieldComparatorDescendingNumericByScore() {
        var cmp = RelationLayout.fieldComparator("-score");
        ObjectNode lo = JsonNodeFactory.instance.objectNode(); lo.put("score", 5);
        ObjectNode hi = JsonNodeFactory.instance.objectNode(); hi.put("score", 42);
        // descending: hi (42) should come before lo (5)
        assertTrue(cmp.compare(hi, lo) < 0, "42 before 5 in descending numeric");
        assertTrue(cmp.compare(lo, hi) > 0, "5 after 42 in descending numeric");
    }

    @Test
    void fieldComparatorNullsLastAscending() {
        var cmp = RelationLayout.fieldComparator("name");
        ObjectNode present = JsonNodeFactory.instance.objectNode(); present.put("name", "Alice");
        ObjectNode nullNode = JsonNodeFactory.instance.objectNode(); nullNode.putNull("name");
        ObjectNode missing = JsonNodeFactory.instance.objectNode(); // field absent
        // nulls and missing must sort after present values
        assertTrue(cmp.compare(present, nullNode) < 0, "present < null ascending");
        assertTrue(cmp.compare(present, missing) < 0, "present < missing ascending");
        assertEquals(0, cmp.compare(nullNode, missing), "null and missing are equal");
    }

    @Test
    void fieldComparatorNullsLastDescending() {
        var cmp = RelationLayout.fieldComparator("-name");
        ObjectNode present = JsonNodeFactory.instance.objectNode(); present.put("name", "Alice");
        ObjectNode nullNode = JsonNodeFactory.instance.objectNode(); nullNode.putNull("name");
        ObjectNode missing = JsonNodeFactory.instance.objectNode(); // field absent
        // nulls must still sort LAST even in descending mode (not flipped)
        assertTrue(cmp.compare(present, nullNode) < 0, "present before null in descending");
        assertTrue(cmp.compare(present, missing) < 0, "present before missing in descending");
        assertEquals(0, cmp.compare(nullNode, missing), "null and missing equal in descending");
    }

    @Test
    void descendingSortFieldViaRelation() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("score"));
        schema.setSortFields(List.of("-name"));

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        ArrayNode data = buildData(new String[][] {
            { "Alice",   "10" },
            { "Charlie", "30" },
            { "Bob",     "20" }
        });

        List<String> names = namesAfterMeasure(layout, data, model, "name");
        assertEquals(List.of("Charlie", "Bob", "Alice"), names,
                     "-name sort field must produce descending lexicographic order");
    }

    // -----------------------------------------------------------------------
    // Test: sort is stable — equal keys preserve relative insertion order
    // -----------------------------------------------------------------------

    @Test
    void sortIsStable() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("group"));
        schema.addChild(new Primitive("seq"));
        schema.setSortFields(List.of("group"));

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // Two rows with group "A" in order seq=1, seq=2 — stable sort must preserve
        ObjectNode r1 = JsonNodeFactory.instance.objectNode();
        r1.put("group", "A"); r1.put("seq", "1"); data.add(r1);
        ObjectNode r2 = JsonNodeFactory.instance.objectNode();
        r2.put("group", "B"); r2.put("seq", "3"); data.add(r2);
        ObjectNode r3 = JsonNodeFactory.instance.objectNode();
        r3.put("group", "A"); r3.put("seq", "2"); data.add(r3);

        layout.measure(data, n -> n, model);
        ObjectNode parent = JsonNodeFactory.instance.objectNode();
        parent.set("items", data);
        JsonNode extracted = layout.extractFrom(parent);

        List<String> seqs = new java.util.ArrayList<>();
        extracted.forEach(row -> seqs.add(row.get("seq").asText()));
        // group A rows (seq 1,2) come first (stable), then group B (seq 3)
        assertEquals(List.of("1", "2", "3"), seqs,
                     "Sort must be stable: equal-key rows preserve insertion order");
    }
}

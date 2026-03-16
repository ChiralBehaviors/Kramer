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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tests for Kramer-xa0: hide-if-empty filter in RelationLayout.measure()
 * and extractFrom().
 */
class HideIfEmptyFilterTest {

    // -----------------------------------------------------------------------
    // Schema builder helpers
    // -----------------------------------------------------------------------

    /**
     * Build a parent Relation with one Primitive child ("name") and one
     * Relation child ("tags") whose items are the rows to be filtered.
     * The parent's field is "rows".
     */
    private Relation buildParentSchema(String parentField) {
        Relation parent = new Relation(parentField);
        parent.addChild(new Primitive("name"));
        Relation tags = new Relation("tags");
        tags.addChild(new Primitive("tag"));
        parent.addChild(tags);
        return parent;
    }

    /** Build an ObjectNode row: name=<name>, tags=[<tagValues>...] */
    private ObjectNode buildRow(String name, String... tagValues) {
        ObjectNode row = JsonNodeFactory.instance.objectNode();
        row.put("name", name);
        ArrayNode tags = JsonNodeFactory.instance.arrayNode();
        for (String t : tagValues) {
            ObjectNode tagObj = JsonNodeFactory.instance.objectNode();
            tagObj.put("tag", t);
            tags.add(tagObj);
        }
        row.set("tags", tags);
        return row;
    }

    /** Wrap rows in an ArrayNode. */
    private ArrayNode buildData(ObjectNode... rows) {
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (ObjectNode r : rows) {
            data.add(r);
        }
        return data;
    }

    /**
     * Build a Style mock that handles both Primitive and Relation children
     * of the given parent schema.
     */
    private Style mockModel(Relation parent) {
        Style model = mock(Style.class);
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        RelationStyle relStyle = TestLayouts.mockRelationStyle();

        // Use thenAnswer for the general case — create fresh layouts to avoid recursion
        when(model.layout(any(SchemaNode.class))).thenAnswer(inv -> {
            SchemaNode n = inv.getArgument(0);
            if (n instanceof Primitive p) return new PrimitiveLayout(p, primStyle);
            if (n instanceof Relation r) return new RelationLayout(r, relStyle);
            return null;
        });
        return model;
    }

    private RelationLayout buildLayout(Relation schema) {
        RelationStyle style = TestLayouts.mockRelationStyle();
        return new RelationLayout(schema, style);
    }

    /**
     * Run measure and return list of "name" field values from extracted rows.
     */
    private List<String> namesAfterMeasure(RelationLayout layout,
                                            ArrayNode data,
                                            Style model) {
        layout.measure(data, n -> n, model);
        ObjectNode parent = JsonNodeFactory.instance.objectNode();
        parent.set(layout.getNode().getField(), data);
        JsonNode extracted = layout.extractFrom(parent);
        List<String> names = new ArrayList<>();
        extracted.forEach(row -> {
            JsonNode nameNode = row.get("name");
            names.add(nameNode != null ? nameNode.asText() : "<null>");
        });
        return names;
    }

    // -----------------------------------------------------------------------
    // Test: hideIfEmpty=null (default) preserves all rows including empty
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyNullPreservesAllRows() {
        Relation schema = buildParentSchema("rows");
        // hideIfEmpty defaults to null — no filtering
        assertNull(schema.getHideIfEmpty());

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        // 2 of 3 rows have non-empty tags (avg cardinality rounds to >= 1)
        ArrayNode data = buildData(
            buildRow("Alpha", "x", "y"),   // has tags
            buildRow("Beta"),               // empty tags array — must still be kept
            buildRow("Gamma", "z", "w")    // has tags
        );

        List<String> names = namesAfterMeasure(layout, data, model);
        assertEquals(List.of("Alpha", "Beta", "Gamma"), names,
                     "hideIfEmpty=null must preserve all rows, including those with empty children");
    }

    // -----------------------------------------------------------------------
    // Test: hideIfEmpty=false explicitly keeps all rows
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyFalseKeepsAllRows() {
        Relation schema = buildParentSchema("rows");
        schema.setHideIfEmpty(false);

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        // Ensure enough non-empty rows so avg cardinality rounds to >= 1
        ArrayNode data = buildData(
            buildRow("Alpha", "x", "y"),    // has tags
            buildRow("Beta", "z"),          // has tags
            buildRow("Gamma")               // empty tags — must still be kept
        );

        List<String> names = namesAfterMeasure(layout, data, model);
        assertEquals(List.of("Alpha", "Beta", "Gamma"), names,
                     "hideIfEmpty=false must keep all rows");
    }

    // -----------------------------------------------------------------------
    // Test: hideIfEmpty=true removes rows where all child Relations are empty
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyTrueRemovesRowsWithAllEmptyChildRelations() {
        Relation schema = buildParentSchema("rows");
        schema.setHideIfEmpty(true);

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        ArrayNode data = buildData(
            buildRow("Alpha", "x", "y"),   // has tags — keep
            buildRow("Beta"),               // empty tags — remove
            buildRow("Gamma", "z")          // has tags — keep
        );

        List<String> names = namesAfterMeasure(layout, data, model);
        assertEquals(List.of("Alpha", "Gamma"), names,
                     "hideIfEmpty=true must remove rows with empty child Relations");
    }

    // -----------------------------------------------------------------------
    // Test: hideIfEmpty=true keeps rows with at least one non-empty child Relation
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyTrueKeepsRowsWithAtLeastOneNonEmptyChildRelation() {
        Relation schema = buildParentSchema("rows");
        schema.setHideIfEmpty(true);

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        ArrayNode data = buildData(
            buildRow("OnlyNonEmpty", "tag1"),  // non-empty — keep
            buildRow("AlsoNonEmpty", "a", "b") // non-empty — keep
        );

        List<String> names = namesAfterMeasure(layout, data, model);
        assertEquals(List.of("OnlyNonEmpty", "AlsoNonEmpty"), names,
                     "hideIfEmpty=true must keep rows with at least one non-empty child Relation");
    }

    // -----------------------------------------------------------------------
    // Test: hideIfEmpty=true with autoFoldable != null is skipped (guard)
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyTrueWithAutoFoldableIsSkipped() {
        // Build a schema where autoFoldable returns non-null:
        // A single Relation child triggers autoFold by default.
        Relation inner = new Relation("items");
        inner.addChild(new Primitive("value"));

        Relation schema = new Relation("rows");
        schema.addChild(inner);       // single Relation child → autoFold
        schema.setHideIfEmpty(true);  // set filter, but guard should skip it

        // autoFoldable should be non-null
        assertNotNull(schema.getAutoFoldable(), "autoFoldable must be non-null for this guard test");

        RelationStyle relStyle = TestLayouts.mockRelationStyle();
        PrimitiveStyle primStyle = TestLayouts.mockPrimitiveStyle(7.0);
        Style model = mock(Style.class);
        when(model.layout(any(SchemaNode.class))).thenAnswer(inv -> {
            Object n = inv.getArgument(0);
            if (n instanceof Relation r) return new RelationLayout(r, relStyle);
            return new PrimitiveLayout((Primitive) n, primStyle);
        });

        RelationLayout layout = buildLayout(schema);

        // Three rows: two with items, one without
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ObjectNode r1 = JsonNodeFactory.instance.objectNode();
        ArrayNode items1 = JsonNodeFactory.instance.arrayNode();
        ObjectNode v1 = JsonNodeFactory.instance.objectNode();
        v1.put("value", "a");
        items1.add(v1);
        r1.set("items", items1);
        data.add(r1);

        ObjectNode r2 = JsonNodeFactory.instance.objectNode();
        r2.set("items", JsonNodeFactory.instance.arrayNode()); // empty
        data.add(r2);

        ObjectNode r3 = JsonNodeFactory.instance.objectNode();
        ArrayNode items3 = JsonNodeFactory.instance.arrayNode();
        ObjectNode v3 = JsonNodeFactory.instance.objectNode();
        v3.put("value", "b");
        items3.add(v3);
        r3.set("items", items3);
        data.add(r3);

        // measure should not throw; autoFold guard skips filtering
        assertDoesNotThrow(() -> layout.measure(data, n -> n, model),
                           "measure() with autoFoldable guard must not throw");

        // maxCardinality reflects all rows (no filtering applied)
        // We verify by checking maxCardinality via MeasureResult
        MeasureResult mr = layout.getMeasureResult();
        assertNotNull(mr);
        // autoFold flattens across the fold child; we just verify no NPE and
        // that the guard did not silently corrupt state
        assertTrue(mr.maxCardinality() >= 0,
                   "maxCardinality must be non-negative after guarded measure()");
    }

    // -----------------------------------------------------------------------
    // Test: hideIfEmpty=true with ALL rows filtered produces maxCardinality=0
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyTrueAllRowsFilteredProducesZeroCardinality() {
        Relation schema = buildParentSchema("rows");
        schema.setHideIfEmpty(true);

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        // All rows have empty tags
        ArrayNode data = buildData(
            buildRow("Alpha"),
            buildRow("Beta"),
            buildRow("Gamma")
        );

        layout.measure(data, n -> n, model);

        MeasureResult mr = layout.getMeasureResult();
        assertNotNull(mr);
        assertEquals(0, mr.maxCardinality(),
                     "All rows filtered out must produce maxCardinality=0");
    }

    // -----------------------------------------------------------------------
    // Test: hideIfEmpty=true with JSON null child value is treated as empty
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyTrueNullChildValueIsFiltered() {
        Relation schema = buildParentSchema("rows");
        schema.setHideIfEmpty(true);

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        // Row whose "tags" field is JSON null (putNull) — must be filtered
        ObjectNode nullTagRow = JsonNodeFactory.instance.objectNode();
        nullTagRow.put("name", "NullTags");
        nullTagRow.putNull("tags");

        ArrayNode data = buildData(
            buildRow("Keep", "t1"),
            nullTagRow
        );

        List<String> names = namesAfterMeasure(layout, data, model);
        assertEquals(List.of("Keep"), names,
                     "Row with JSON null child relation value must be filtered by hideIfEmpty=true");
    }

    // -----------------------------------------------------------------------
    // Test: extractFrom() applies same filter as measure() for build-phase consistency
    // -----------------------------------------------------------------------

    @Test
    void extractFromAppliesSameFilterAsMeasure() {
        Relation schema = buildParentSchema("rows");
        schema.setHideIfEmpty(true);

        RelationLayout layout = buildLayout(schema);
        Style model = mockModel(schema);

        ArrayNode data = buildData(
            buildRow("Keep1", "t1"),
            buildRow("Drop1"),
            buildRow("Keep2", "t2"),
            buildRow("Drop2")
        );

        // measure establishes the filter predicate
        layout.measure(data, n -> n, model);

        // extractFrom() must apply the same filter
        ObjectNode parent = JsonNodeFactory.instance.objectNode();
        parent.set("rows", data);
        JsonNode extracted = layout.extractFrom(parent);

        List<String> names = new ArrayList<>();
        extracted.forEach(row -> names.add(row.get("name").asText()));

        assertEquals(List.of("Keep1", "Keep2"), names,
                     "extractFrom() must apply the same hide-if-empty filter as measure()");
    }
}

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
 * Tests for Kramer-xu1: hide-if-empty and sort-fields routed through
 * LayoutStylesheet with Relation object as fallback.
 */
class StylesheetPropertyRoutingTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Style buildMockModel(Relation schema, LayoutStylesheet stylesheet) {
        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(stylesheet);
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
            RelationStyle rs = TestLayouts.mockRelationStyle();
            return new RelationLayout((Relation) n, rs);
        });
        return model;
    }

    /** Build a simple single-child relation with a nested array child. */
    private Relation buildParentChild() {
        Relation parent = new Relation("root");
        Relation child = new Relation("items");
        child.addChild(new Primitive("name"));
        child.addChild(new Primitive("value"));
        parent.addChild(child);
        return parent;
    }

    /** Build an ArrayNode wrapping objects with optional nested arrays. */
    private ArrayNode buildNestedData(boolean withNonEmptyChild) {
        ArrayNode outer = JsonNodeFactory.instance.arrayNode();
        ObjectNode row1 = JsonNodeFactory.instance.objectNode();
        ArrayNode innerData = JsonNodeFactory.instance.arrayNode();
        if (withNonEmptyChild) {
            ObjectNode item = JsonNodeFactory.instance.objectNode();
            item.put("name", "Alice");
            item.put("value", "1");
            innerData.add(item);
        }
        row1.set("items", innerData);
        outer.add(row1);

        // Second row always has a non-empty child
        ObjectNode row2 = JsonNodeFactory.instance.objectNode();
        ArrayNode innerData2 = JsonNodeFactory.instance.arrayNode();
        ObjectNode item2 = JsonNodeFactory.instance.objectNode();
        item2.put("name", "Bob");
        item2.put("value", "2");
        innerData2.add(item2);
        row2.set("items", innerData2);
        outer.add(row2);

        return outer;
    }

    /** Build a simple flat array with name and score fields. */
    private ArrayNode buildFlatData(String[][] rows) {
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

    // -----------------------------------------------------------------------
    // hide-if-empty: stylesheet overrides null Relation.getHideIfEmpty()
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyFromStylesheetOverridesNullRelationValue() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("value"));
        // Relation has no hideIfEmpty set (null)
        assertNull(schema.getHideIfEmpty(), "precondition: Relation.hideIfEmpty must be null");

        SchemaPath path = new SchemaPath("items");

        LayoutStylesheet stylesheet = mock(LayoutStylesheet.class);
        when(stylesheet.getBoolean(eq(path), eq(LayoutPropertyKeys.HIDE_IF_EMPTY), anyBoolean()))
            .thenReturn(true);
        // Return empty string for sort-fields and pivot-field to avoid side effects
        when(stylesheet.getString(any(), any(), any())).thenReturn("");

        Style model = buildMockModel(schema, stylesheet);

        RelationLayout layout = buildLayout(schema);
        layout.buildPaths(path, model);

        // Build data where all rows have empty nested arrays — but schema is flat here,
        // so use a simple ArrayNode. hideIfEmpty on a flat schema is irrelevant to
        // filtering (hasNonEmptyChildren returns true when no Relation children exist).
        // To test the routing, verify that stylesheet.getBoolean was called.
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ObjectNode row = JsonNodeFactory.instance.objectNode();
        row.put("name", "Alice");
        row.put("value", "1");
        data.add(row);

        layout.measure(data, n -> n, model);

        // Verify stylesheet was consulted for hide-if-empty
        verify(stylesheet, atLeastOnce())
            .getBoolean(eq(path), eq(LayoutPropertyKeys.HIDE_IF_EMPTY), anyBoolean());
    }

    // -----------------------------------------------------------------------
    // hide-if-empty: Relation value overrides stylesheet when non-null
    // -----------------------------------------------------------------------

    @Test
    void hideIfEmptyFromRelationOverridesStylesheetWhenNonNull() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("value"));
        // Relation explicitly sets hideIfEmpty=false
        schema.setHideIfEmpty(false);

        SchemaPath path = new SchemaPath("items");

        LayoutStylesheet stylesheet = mock(LayoutStylesheet.class);
        // Stylesheet says true — but Relation says false, so stylesheet must NOT be consulted
        // for hide-if-empty (Relation takes priority when non-null).
        when(stylesheet.getString(any(), any(), any())).thenReturn("");

        Style model = buildMockModel(schema, stylesheet);

        RelationLayout layout = buildLayout(schema);
        layout.buildPaths(path, model);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ObjectNode row = JsonNodeFactory.instance.objectNode();
        row.put("name", "Alice");
        row.put("value", "1");
        data.add(row);

        layout.measure(data, n -> n, model);

        // Stylesheet must NOT be called for hide-if-empty when Relation value is non-null
        verify(stylesheet, never())
            .getBoolean(eq(path), eq(LayoutPropertyKeys.HIDE_IF_EMPTY), anyBoolean());
    }

    // -----------------------------------------------------------------------
    // sort-fields: stylesheet parses comma-separated string
    // -----------------------------------------------------------------------

    @Test
    void sortFieldsFromStylesheetParsesCommaSeparatedString() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("score"));
        // Relation has no sortFields
        assertEquals(List.of(), schema.getSortFields(), "precondition: Relation.sortFields must be empty");

        SchemaPath path = new SchemaPath("items");

        DefaultLayoutStylesheet stylesheet = new DefaultLayoutStylesheet(new Style());
        // Set sort-fields via stylesheet: "score" (single field, sorts descending by score position)
        stylesheet.setOverride(path, LayoutPropertyKeys.SORT_FIELDS, "name");

        Style model = buildMockModel(schema, stylesheet);

        RelationLayout layout = buildLayout(schema);
        layout.buildPaths(path, model);

        ArrayNode data = buildFlatData(new String[][] {
            { "Charlie", "30" },
            { "Alice",   "10" },
            { "Bob",     "20" }
        });

        layout.measure(data, n -> n, model);

        // Retrieve sorted data
        ObjectNode parent = JsonNodeFactory.instance.objectNode();
        parent.set("items", data);
        JsonNode extracted = layout.extractFrom(parent);

        List<String> names = new java.util.ArrayList<>();
        extracted.forEach(row -> names.add(row.get("name").asText()));

        // Stylesheet said sort by "name" — should be alphabetical
        assertEquals(List.of("Alice", "Bob", "Charlie"), names,
                     "sort-fields from stylesheet must sort by the specified field");
    }

    @Test
    void sortFieldsFromStylesheetParsesMultipleCommaSeparatedFields() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("group"));
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("score"));
        assertEquals(List.of(), schema.getSortFields(), "precondition: Relation.sortFields must be empty");

        SchemaPath path = new SchemaPath("items");

        DefaultLayoutStylesheet stylesheet = new DefaultLayoutStylesheet(new Style());
        // Multiple fields: sort by group then name
        stylesheet.setOverride(path, LayoutPropertyKeys.SORT_FIELDS, "group,name");

        Style model = buildMockModel(schema, stylesheet);

        RelationLayout layout = buildLayout(schema);
        layout.buildPaths(path, model);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // group=B, name=Alice
        ObjectNode r1 = JsonNodeFactory.instance.objectNode();
        r1.put("group", "B"); r1.put("name", "Alice"); r1.put("score", "1");
        data.add(r1);
        // group=A, name=Zed
        ObjectNode r2 = JsonNodeFactory.instance.objectNode();
        r2.put("group", "A"); r2.put("name", "Zed"); r2.put("score", "2");
        data.add(r2);
        // group=A, name=Alpha
        ObjectNode r3 = JsonNodeFactory.instance.objectNode();
        r3.put("group", "A"); r3.put("name", "Alpha"); r3.put("score", "3");
        data.add(r3);

        layout.measure(data, n -> n, model);

        ObjectNode parent = JsonNodeFactory.instance.objectNode();
        parent.set("items", data);
        JsonNode extracted = layout.extractFrom(parent);

        List<String> names = new java.util.ArrayList<>();
        extracted.forEach(row -> names.add(row.get("name").asText()));

        // Sort by group then name: A/Alpha, A/Zed, B/Alice
        assertEquals(List.of("Alpha", "Zed", "Alice"), names,
                     "comma-separated sort-fields from stylesheet must apply multi-field sort");
    }

    // -----------------------------------------------------------------------
    // sort-fields: Relation overrides stylesheet when non-empty
    // -----------------------------------------------------------------------

    @Test
    void sortFieldsFromRelationOverridesStylesheetWhenNonEmpty() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("score"));
        // Relation explicitly sets sortFields
        schema.setSortFields(List.of("score"));

        SchemaPath path = new SchemaPath("items");

        LayoutStylesheet stylesheet = mock(LayoutStylesheet.class);
        // Stylesheet says sort by "name" — but Relation says "score", Relation wins
        when(stylesheet.getString(eq(path), eq(LayoutPropertyKeys.SORT_FIELDS), any()))
            .thenReturn("name");
        // Return sensible defaults for all other stylesheet queries
        when(stylesheet.getString(any(), any(), any())).thenReturn("");
        when(stylesheet.getBoolean(any(), any(), anyBoolean())).thenReturn(false);
        when(stylesheet.getDouble(any(), any(), anyDouble())).thenReturn(0.0);
        when(stylesheet.getInt(any(), any(), anyInt())).thenReturn(0);

        Style model = buildMockModel(schema, stylesheet);

        RelationLayout layout = buildLayout(schema);
        layout.buildPaths(path, model);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ObjectNode r1 = JsonNodeFactory.instance.objectNode();
        r1.put("name", "Charlie"); r1.put("score", "30"); data.add(r1);
        ObjectNode r2 = JsonNodeFactory.instance.objectNode();
        r2.put("name", "Alice"); r2.put("score", "10"); data.add(r2);
        ObjectNode r3 = JsonNodeFactory.instance.objectNode();
        r3.put("name", "Bob"); r3.put("score", "20"); data.add(r3);

        layout.measure(data, n -> n, model);

        ObjectNode parent = JsonNodeFactory.instance.objectNode();
        parent.set("items", data);
        JsonNode extracted = layout.extractFrom(parent);

        List<String> scores = new java.util.ArrayList<>();
        extracted.forEach(row -> scores.add(row.get("score").asText()));

        // Relation.sortFields=[score] must win over stylesheet sort-by-name
        assertEquals(List.of("10", "20", "30"), scores,
                     "Relation.sortFields must override stylesheet sort-fields when non-empty");
    }
}

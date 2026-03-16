// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class HtmlLayoutRendererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static LayoutDecisionNode leaf(String name) {
        var path = new SchemaPath(name);
        return new LayoutDecisionNode(path, path.leaf(), null, null, null, null, null, null);
    }

    private static LayoutDecisionNode parent(String name, List<LayoutDecisionNode> children) {
        var path = new SchemaPath(name);
        return new LayoutDecisionNode(path, path.leaf(), null, null, null, null, null, children);
    }

    @Test
    void leafNodeRendersAsSpan() {
        var renderer = new HtmlLayoutRenderer();
        var node = leaf("score");
        JsonNode data = MAPPER.getNodeFactory().textNode("42");

        var result = renderer.render(node, data);

        assertEquals("<span class=\"score\">42</span>", result);
    }

    @Test
    void relationNodeRendersAsDiv() {
        var child = leaf("name");
        var root = parent("person", List.of(child));

        ObjectNode data = MAPPER.createObjectNode();
        data.put("name", "Alice");

        var renderer = new HtmlLayoutRenderer();
        var result = renderer.render(root, data);

        assertEquals("<div class=\"person\"><span class=\"name\">Alice</span></div>", result);
    }

    @Test
    void nestedTreeProducesCorrectStructure() {
        var grandchild = leaf("value");
        var child = parent("nested", List.of(grandchild));
        var root = parent("top", List.of(child));

        ObjectNode innerData = MAPPER.createObjectNode();
        innerData.put("value", "deep");
        ObjectNode outerData = MAPPER.createObjectNode();
        outerData.set("nested", innerData);

        var renderer = new HtmlLayoutRenderer();
        var result = renderer.render(root, outerData);

        assertEquals(
            "<div class=\"top\"><div class=\"nested\"><span class=\"value\">deep</span></div></div>",
            result);
    }

    @Test
    void nullDataRendersEmptySpan() {
        var renderer = new HtmlLayoutRenderer();
        var node = leaf("title");

        var result = renderer.render(node, null);

        assertEquals("<span class=\"title\"></span>", result);
    }

    @Test
    void cssClassUsesSanitizedFieldName() {
        var renderer = new HtmlLayoutRenderer();
        var node = leaf("my field");
        JsonNode data = MAPPER.getNodeFactory().textNode("x");

        var result = renderer.render(node, data);

        // "my field" sanitizes to "my_field"
        assertTrue(result.contains("class=\"my_field\""),
                   "Expected sanitized CSS class in: " + result);
    }

    @Test
    void numericLeadingCharacterSanitized() {
        var renderer = new HtmlLayoutRenderer();
        var node = leaf("1value");
        JsonNode data = MAPPER.getNodeFactory().textNode("ok");

        var result = renderer.render(node, data);

        // "1value" sanitizes to "_1value"
        assertTrue(result.contains("class=\"_1value\""),
                   "Expected leading-digit sanitization in: " + result);
    }

    @Test
    void arrayDataRendersEachItemAsListItem() {
        var renderer = new HtmlLayoutRenderer();
        var node = leaf("tags");
        ArrayNode arr = MAPPER.createArrayNode();
        arr.add("alpha");
        arr.add("beta");

        var result = renderer.render(node, arr);

        assertTrue(result.contains("<li>alpha</li>"), "Missing first list item in: " + result);
        assertTrue(result.contains("<li>beta</li>"), "Missing second list item in: " + result);
        assertTrue(result.startsWith("<ul class=\"tags\">"), "Missing ul wrapper in: " + result);
        assertTrue(result.endsWith("</ul>"), "Missing closing ul in: " + result);
    }

    @Test
    void htmlSpecialCharactersAreEscaped() {
        var renderer = new HtmlLayoutRenderer();
        var node = leaf("content");
        JsonNode data = MAPPER.getNodeFactory().textNode("<b>bold</b> & \"quoted\"");

        var result = renderer.render(node, data);

        assertTrue(result.contains("&lt;b&gt;bold&lt;/b&gt;"),
                   "Angle brackets not escaped in: " + result);
        assertTrue(result.contains("&amp;"),
                   "Ampersand not escaped in: " + result);
        assertTrue(result.contains("&quot;quoted&quot;"),
                   "Quotes not escaped in: " + result);
    }

    // --- table mode tests ---

    private static LayoutDecisionNode tableParent(String name, List<LayoutDecisionNode> children) {
        var path = new SchemaPath(name);
        var layoutResult = new LayoutResult(
            RelationRenderMode.TABLE,
            PrimitiveRenderMode.TEXT,
            false, 0.0, 0.0, 0.0,
            List.of()
        );
        return new LayoutDecisionNode(path, path.leaf(), null, layoutResult, null, null, null, children);
    }

    @Test
    void tableModeRendersAsHtmlTable() {
        var colA = leaf("name");
        var colB = leaf("score");
        var node = tableParent("results", List.of(colA, colB));

        ArrayNode rows = MAPPER.createArrayNode();
        ObjectNode row = MAPPER.createObjectNode();
        row.put("name", "Alice");
        row.put("score", "10");
        rows.add(row);

        var renderer = new HtmlLayoutRenderer();
        var result = renderer.render(node, rows);

        assertTrue(result.startsWith("<table"), "Expected <table> element, got: " + result);
        assertTrue(result.contains("</table>"), "Missing </table> in: " + result);
        assertFalse(result.contains("<div"), "Should not contain <div> in table mode: " + result);
    }

    @Test
    void tableModeRendersColumnHeaders() {
        var colA = leaf("name");
        var colB = leaf("age");
        var node = tableParent("people", List.of(colA, colB));

        ArrayNode rows = MAPPER.createArrayNode();
        rows.add(MAPPER.createObjectNode());

        var renderer = new HtmlLayoutRenderer();
        var result = renderer.render(node, rows);

        assertTrue(result.contains("<th"), "Expected <th> header elements in: " + result);
        assertTrue(result.contains(">name<"), "Expected 'name' header in: " + result);
        assertTrue(result.contains(">age<"), "Expected 'age' header in: " + result);
    }

    @Test
    void tableModeMultipleRowsRenderAsMultipleTr() {
        var col = leaf("val");
        var node = tableParent("items", List.of(col));

        ArrayNode rows = MAPPER.createArrayNode();
        ObjectNode r1 = MAPPER.createObjectNode();
        r1.put("val", "foo");
        rows.add(r1);
        ObjectNode r2 = MAPPER.createObjectNode();
        r2.put("val", "bar");
        rows.add(r2);
        ObjectNode r3 = MAPPER.createObjectNode();
        r3.put("val", "baz");
        rows.add(r3);

        var renderer = new HtmlLayoutRenderer();
        var result = renderer.render(node, rows);

        int trCount = countOccurrences(result, "<tr");
        // 1 header row + 3 data rows
        assertEquals(4, trCount, "Expected 4 <tr> elements (1 header + 3 data), got " + trCount + " in: " + result);
        assertTrue(result.contains(">foo<"), "Missing row value 'foo' in: " + result);
        assertTrue(result.contains(">bar<"), "Missing row value 'bar' in: " + result);
        assertTrue(result.contains(">baz<"), "Missing row value 'baz' in: " + result);
    }

    @Test
    void outlineModeStillRendersAsDiv() {
        var child = leaf("name");
        var root = parent("person", List.of(child));

        ObjectNode data = MAPPER.createObjectNode();
        data.put("name", "Bob");

        var renderer = new HtmlLayoutRenderer();
        var result = renderer.render(root, data);

        assertTrue(result.startsWith("<div"), "Outline mode should produce <div>, got: " + result);
        assertFalse(result.contains("<table"), "Outline mode should not produce <table> in: " + result);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}

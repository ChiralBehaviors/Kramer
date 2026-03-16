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
}

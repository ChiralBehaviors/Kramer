// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class LayoutRendererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Minimal concrete renderer that records calls and returns string tokens. */
    private static class StringRenderer extends AbstractLayoutRenderer<String> {
        int primitiveCallCount = 0;
        int relationCallCount  = 0;

        @Override
        protected String renderPrimitive(LayoutDecisionNode node, JsonNode data) {
            primitiveCallCount++;
            return "primitive:" + node.fieldName() + "=" + (data == null ? "null" : data.asText());
        }

        @Override
        protected String renderRelation(LayoutDecisionNode node, JsonNode data, List<String> children) {
            relationCallCount++;
            return "relation:" + node.fieldName() + "[" + String.join(",", children) + "]";
        }
    }

    private static LayoutDecisionNode leaf(String name) {
        var path = new SchemaPath(name);
        return new LayoutDecisionNode(path, path.leaf(), null, null, null, null, null, null);
    }

    private static LayoutDecisionNode parent(String name, List<LayoutDecisionNode> children) {
        var path = new SchemaPath(name);
        return new LayoutDecisionNode(path, path.leaf(), null, null, null, null, null, children);
    }

    @Test
    void leafNodeCallsRenderPrimitive() {
        var renderer = new StringRenderer();
        var node = leaf("score");
        JsonNode data = MAPPER.getNodeFactory().textNode("42");

        var result = renderer.render(node, data);

        assertEquals(1, renderer.primitiveCallCount);
        assertEquals(0, renderer.relationCallCount);
        assertEquals("primitive:score=42", result);
    }

    @Test
    void treeNodeRecursesThroughChildren() {
        var child1 = leaf("name");
        var child2 = leaf("age");
        var root = parent("person", List.of(child1, child2));

        ObjectNode data = MAPPER.createObjectNode();
        data.put("name", "Alice");
        data.put("age", 30);

        var renderer = new StringRenderer();
        var result = renderer.render(root, data);

        assertEquals(2, renderer.primitiveCallCount);
        assertEquals(1, renderer.relationCallCount);
        assertEquals("relation:person[primitive:name=Alice,primitive:age=30]", result);
    }

    @Test
    void childDataExtractionUsesFieldName() {
        // Verify that child data is extracted via data.get(child.fieldName())
        var child = leaf("status");
        var root = parent("item", List.of(child));

        ObjectNode data = MAPPER.createObjectNode();
        data.put("status", "active");
        // extra field not in schema — should be ignored
        data.put("other", "ignored");

        var renderer = new StringRenderer();
        renderer.render(root, data);

        // Only "status" should be passed to renderPrimitive
        assertEquals(1, renderer.primitiveCallCount);
        // Confirm by inspecting what renderPrimitive received via result
        var result = renderer.render(root, data);
        assertTrue(result.contains("primitive:status=active"));
        assertFalse(result.contains("ignored"));
    }

    @Test
    void nullDataHandledGracefully() {
        var renderer = new StringRenderer();

        // null data at leaf
        var leafNode = leaf("x");
        var leafResult = renderer.render(leafNode, null);
        assertEquals("primitive:x=null", leafResult);

        // null data at relation — children should receive null child data
        var child = leaf("y");
        var root = parent("z", List.of(child));
        var result = renderer.render(root, null);
        assertTrue(result.contains("primitive:y=null"));
        assertTrue(result.contains("relation:z["));
    }

    @Test
    void deeplyNestedTreeIsRendered() {
        // grandchild -> child -> root
        var grandchild = leaf("value");
        var child = parent("nested", List.of(grandchild));
        var root = parent("top", List.of(child));

        ObjectNode grandchildData = MAPPER.createObjectNode();
        grandchildData.put("value", "deep");
        ObjectNode childData = MAPPER.createObjectNode();
        childData.set("nested", grandchildData);
        ObjectNode rootData = MAPPER.createObjectNode();
        rootData.set("top", childData);  // top is not used by root's own render — root IS the top

        // root data contains "nested" field
        var renderer = new StringRenderer();
        var result = renderer.render(root, childData);

        assertEquals(1, renderer.primitiveCallCount);
        assertEquals(2, renderer.relationCallCount);
        assertTrue(result.contains("primitive:value=deep"));
    }
}

// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * TDD tests for Jackson serialization of LayoutDecisionNode (Kramer-c3l).
 *
 * Verifies that:
 *   - LayoutDecisionNode serializes to JSON without error
 *   - MeasureResult.extractor is excluded from serialization
 *   - Nested childNodes serialize correctly
 */
class LayoutDecisionNodeSerializationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static MeasureResult measureResult() {
        return new MeasureResult(10.0, 100.0, 90.0, 120.0, 3, false, 1, 5,
                                 node -> node, List.of(), null, null, null);
    }

    private static MeasureResult measureResultWithChildren() {
        MeasureResult child = new MeasureResult(5.0, 50.0, 45.0, 60.0, 1, false, 0, 2,
                                                node -> node, List.of(), null, null, null);
        return new MeasureResult(10.0, 100.0, 90.0, 120.0, 3, false, 1, 5,
                                 node -> node, List.of(child), null, null, null);
    }

    private static LayoutResult layoutResult() {
        return new LayoutResult(RelationRenderMode.OUTLINE, PrimitiveRenderMode.TEXT,
                                false, 0.0, 0.0, 100.0, List.of());
    }

    private static LayoutDecisionNode leafNode(String field) {
        return new LayoutDecisionNode(
            new SchemaPath("root", field),
            field,
            measureResult(),
            layoutResult(),
            null, null, null, null
        );
    }

    // -----------------------------------------------------------------------
    // 1. LayoutDecisionNode serializes to JSON without error
    // -----------------------------------------------------------------------

    @Test
    void layoutDecisionNodeSerializesToJsonWithoutError() throws Exception {
        LayoutDecisionNode node = leafNode("price");
        String json = MAPPER.writeValueAsString(node);
        assertNotNull(json);
        assertFalse(json.isEmpty());
    }

    // -----------------------------------------------------------------------
    // 2. extractor field is excluded from serialization
    // -----------------------------------------------------------------------

    @Test
    void extractorFieldExcludedFromMeasureResultSerialization() throws Exception {
        MeasureResult mr = measureResult();
        String json = MAPPER.writeValueAsString(mr);
        JsonNode tree = MAPPER.readTree(json);
        assertFalse(tree.has("extractor"),
                    "extractor must be excluded from MeasureResult JSON serialization");
    }

    @Test
    void extractorExcludedWhenMeasureResultNestedInDecisionNode() throws Exception {
        LayoutDecisionNode node = leafNode("qty");
        String json = MAPPER.writeValueAsString(node);
        // extractor must not appear anywhere in the serialized tree
        assertFalse(json.contains("\"extractor\""),
                    "extractor must not appear in serialized LayoutDecisionNode JSON");
    }

    // -----------------------------------------------------------------------
    // 3. Nested childNodes serialize correctly
    // -----------------------------------------------------------------------

    @Test
    void nestedChildNodesSerializeCorrectly() throws Exception {
        LayoutDecisionNode child1 = leafNode("firstName");
        LayoutDecisionNode child2 = leafNode("lastName");

        LayoutDecisionNode parent = new LayoutDecisionNode(
            new SchemaPath("root"),
            "root",
            measureResult(),
            layoutResult(),
            null, null, null,
            List.of(child1, child2)
        );

        String json = MAPPER.writeValueAsString(parent);
        JsonNode tree = MAPPER.readTree(json);

        assertTrue(tree.has("childNodes"), "childNodes field must be present in JSON");
        assertEquals(2, tree.get("childNodes").size(), "childNodes must serialize both children");
    }

    // -----------------------------------------------------------------------
    // 4. MeasureResult childResults serialize correctly (extractor excluded throughout)
    // -----------------------------------------------------------------------

    @Test
    void measureResultChildResultsSerializeWithoutExtractor() throws Exception {
        MeasureResult mr = measureResultWithChildren();
        String json = MAPPER.writeValueAsString(mr);
        JsonNode tree = MAPPER.readTree(json);

        assertTrue(tree.has("childResults"), "childResults must be present");
        assertEquals(1, tree.get("childResults").size());
        // extractor must not appear anywhere, including nested
        assertFalse(json.contains("\"extractor\""),
                    "extractor must not appear in childResults serialization");
    }

    // -----------------------------------------------------------------------
    // 5. fieldName and path serialize correctly
    // -----------------------------------------------------------------------

    @Test
    void fieldNameAndPathSerializeCorrectly() throws Exception {
        LayoutDecisionNode node = leafNode("amount");
        String json = MAPPER.writeValueAsString(node);
        JsonNode tree = MAPPER.readTree(json);

        assertTrue(tree.has("fieldName"), "fieldName must be present");
        assertEquals("amount", tree.get("fieldName").asText());
    }
}

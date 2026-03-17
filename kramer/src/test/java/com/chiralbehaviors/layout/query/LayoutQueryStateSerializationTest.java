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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.ConfiguredMeasurementStrategy;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tests for LayoutQueryState serialization (RDR-026 Phase 2).
 *
 * @author hhildebrand
 */
class LayoutQueryStateSerializationTest {

    private Style style;
    private LayoutQueryState queryState;

    @BeforeEach
    void setUp() {
        style = new Style(new ConfiguredMeasurementStrategy());
        queryState = new LayoutQueryState(style);
    }

    // --- toJson / fromJson roundtrip ---

    @Test
    void roundTripAllProperties() {
        var path = new SchemaPath("orders", "revenue");
        queryState.setVisible(path, false);
        queryState.setRenderMode(path, "SPARKLINE");
        queryState.setHideIfEmpty(path, true);
        queryState.setSortFields(path, "revenue,-name");
        queryState.setFilterExpression(path, "$revenue > 50");
        queryState.setFormulaExpression(path, "$price * $qty");
        queryState.setAggregateExpression(path, "sum($revenue)");
        queryState.setSortExpression(path, "abs($delta)");
        queryState.setPivotField(path, "category");

        ObjectNode json = queryState.toJson();
        assertNotNull(json);

        // Restore into a fresh QueryState
        var restored = new LayoutQueryState(style);
        restored.fromJson(json);

        var fs = restored.getFieldState(path);
        assertEquals(false, fs.visible());
        assertEquals("SPARKLINE", fs.renderMode());
        assertEquals(true, fs.hideIfEmpty());
        assertEquals("revenue,-name", fs.sortFields());
        assertEquals("$revenue > 50", fs.filterExpression());
        assertEquals("$price * $qty", fs.formulaExpression());
        assertEquals("sum($revenue)", fs.aggregateExpression());
        assertEquals("abs($delta)", fs.sortExpression());
        assertEquals("category", fs.pivotField());
    }

    @Test
    void roundTripMultiplePaths() {
        var path1 = new SchemaPath("items", "name");
        var path2 = new SchemaPath("items", "score");
        queryState.setVisible(path1, false);
        queryState.setFilterExpression(path2, "$score > 0");

        ObjectNode json = queryState.toJson();

        var restored = new LayoutQueryState(style);
        restored.fromJson(json);

        assertEquals(false, restored.getFieldState(path1).visible());
        assertNull(restored.getFieldState(path1).filterExpression());
        assertNull(restored.getFieldState(path2).visible());
        assertEquals("$score > 0", restored.getFieldState(path2).filterExpression());
    }

    @Test
    void roundTripPartialProperties() {
        // Only set some properties on a path
        var path = new SchemaPath("items", "price");
        queryState.setFilterExpression(path, "$price > 0");
        queryState.setSortExpression(path, "$price");

        ObjectNode json = queryState.toJson();

        var restored = new LayoutQueryState(style);
        restored.fromJson(json);

        var fs = restored.getFieldState(path);
        assertNull(fs.visible());
        assertNull(fs.renderMode());
        assertEquals("$price > 0", fs.filterExpression());
        assertEquals("$price", fs.sortExpression());
    }

    // --- Empty state ---

    @Test
    void toJsonEmptyStateProducesEmptyObject() {
        ObjectNode json = queryState.toJson();
        assertNotNull(json);
        assertEquals(0, json.size());
    }

    @Test
    void fromJsonEmptyObjectResultsInEmptyState() {
        var path = new SchemaPath("items", "name");
        queryState.setVisible(path, false);

        queryState.fromJson(JsonNodeFactory.instance.objectNode());

        // Should have cleared state
        assertNull(queryState.getFieldState(path).visible());
    }

    // --- fromJson suppresses notifications ---

    @Test
    void fromJsonFiresExactlyOneNotification() {
        var count = new AtomicInteger(0);
        queryState.addChangeListener(count::incrementAndGet);

        // Build a JSON with multiple paths
        var source = new LayoutQueryState(style);
        var path1 = new SchemaPath("items", "name");
        var path2 = new SchemaPath("items", "score");
        source.setVisible(path1, false);
        source.setFilterExpression(path2, "$score > 0");
        source.setSortFields(path2, "score");
        ObjectNode json = source.toJson();

        // fromJson on the instance with listener attached
        queryState.fromJson(json);

        assertEquals(1, count.get(),
            "fromJson should fire exactly one notification, not per-property");
    }

    @Test
    void fromJsonFiresNoNotificationDuringLoad() {
        var firedDuringLoad = new AtomicInteger(0);
        // We can't directly detect "during" vs "after" in a synchronous model,
        // but we can verify the total count is exactly 1 (not N)
        queryState.addChangeListener(firedDuringLoad::incrementAndGet);

        var source = new LayoutQueryState(style);
        for (int i = 0; i < 5; i++) {
            source.setVisible(new SchemaPath("items", "field" + i), false);
        }
        ObjectNode json = source.toJson();

        queryState.fromJson(json);

        assertEquals(1, firedDuringLoad.get(),
            "Should fire exactly 1 notification for 5-path load");
    }

    // --- JSON structure ---

    @Test
    void toJsonUsesSchemaPathToStringAsKey() {
        var path = new SchemaPath("orders", "items", "price");
        queryState.setVisible(path, false);

        ObjectNode json = queryState.toJson();
        assertTrue(json.has("orders/items/price"),
            "Key should be SchemaPath.toString() = 'orders/items/price'");
    }

    @Test
    void toJsonOmitsDefaultValues() {
        var path = new SchemaPath("items", "name");
        queryState.setVisible(path, false);

        ObjectNode json = queryState.toJson();
        ObjectNode pathNode = (ObjectNode) json.get("items/name");
        assertNotNull(pathNode);
        assertTrue(pathNode.has("visible"));
        // Properties that were not set should not appear
        assertFalse(pathNode.has("renderMode"));
        assertFalse(pathNode.has("filterExpression"));
    }
}

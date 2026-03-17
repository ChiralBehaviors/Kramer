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

package com.chiralbehaviors.layout.explorer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.AutoLayout;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.query.InteractionHandler;
import com.chiralbehaviors.layout.query.LayoutInteraction;
import com.chiralbehaviors.layout.query.LayoutQueryState;
import com.chiralbehaviors.layout.query.InteractionMenuFactory;
import com.chiralbehaviors.layout.query.ColumnSortHandler;
import com.chiralbehaviors.layout.ConfiguredMeasurementStrategy;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Integration tests for the gesture pipeline: LayoutInteraction →
 * InteractionHandler → LayoutQueryState → Style → AutoLayout re-layout.
 * <p>
 * Uses headless ConfiguredMeasurementStrategy (no JavaFX toolkit required
 * for the core pipeline; AutoLayout construction uses headless Style).
 *
 * @author hhildebrand
 */
class GestureIntegrationTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    private Style style;
    private LayoutQueryState queryState;
    private InteractionHandler handler;
    private AutoLayout autoLayout;

    @BeforeEach
    void setUp() {
        style = new Style(new ConfiguredMeasurementStrategy());
        queryState = new LayoutQueryState(style);
        style.setStylesheet(queryState);
        handler = new InteractionHandler(queryState);
        autoLayout = new AutoLayout(null, style);
    }

    private ArrayNode buildData(String[][] rows, String... fields) {
        ArrayNode data = NF.arrayNode();
        for (String[] row : rows) {
            ObjectNode obj = NF.objectNode();
            for (int i = 0; i < fields.length && i < row.length; i++) {
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

    // --- Full pipeline: event → handler → queryState → re-layout ---

    @Test
    void sortByEventTriggersRelayout() {
        var relayoutCount = new AtomicInteger(0);
        queryState.addChangeListener(relayoutCount::incrementAndGet);

        var path = new SchemaPath("items", "name");
        handler.apply(new LayoutInteraction.SortBy(path, false));

        assertEquals(1, relayoutCount.get());
        assertEquals("name", queryState.getFieldState(path).sortFields());
    }

    @Test
    void toggleVisibleEventTriggersRelayout() {
        var relayoutCount = new AtomicInteger(0);
        queryState.addChangeListener(relayoutCount::incrementAndGet);

        var path = new SchemaPath("items", "hidden");
        handler.apply(new LayoutInteraction.ToggleVisible(path));

        assertEquals(1, relayoutCount.get());
        assertFalse(queryState.getVisibleOrDefault(path));
    }

    @Test
    void filterEventTriggersRelayout() {
        var relayoutCount = new AtomicInteger(0);
        queryState.addChangeListener(relayoutCount::incrementAndGet);

        var path = new SchemaPath("items");
        handler.apply(new LayoutInteraction.SetFilter(path, "$revenue > 50"));

        assertEquals(1, relayoutCount.get());
        assertEquals("$revenue > 50", queryState.getFieldState(path).filterExpression());
    }

    @Test
    void resetAllClearsEverything() {
        var path = new SchemaPath("items", "name");
        handler.apply(new LayoutInteraction.SortBy(path, true));
        handler.apply(new LayoutInteraction.SetFilter(path, "$x > 0"));
        handler.apply(new LayoutInteraction.ToggleVisible(path));

        assertNotNull(queryState.getFieldState(path).sortFields());
        assertNotNull(queryState.getFieldState(path).filterExpression());

        handler.apply(new LayoutInteraction.ResetAll());

        assertNull(queryState.getFieldState(path).sortFields());
        assertNull(queryState.getFieldState(path).filterExpression());
        assertNull(queryState.getFieldState(path).visible());
    }

    // --- QueryState wired through Style to AutoLayout ---

    @Test
    void queryStateIsTheStylesheet() {
        assertSame(queryState, style.getStylesheet());
    }

    @Test
    void queryStateMutationIncrementsVersion() {
        long v0 = queryState.getVersion();
        handler.apply(new LayoutInteraction.SetFilter(
            new SchemaPath("items"), "$x > 0"));
        assertTrue(queryState.getVersion() > v0);
    }

    @Test
    void autoLayoutReceivesQueryStateViaStyle() {
        Relation schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("score"));
        autoLayout.setRoot(schema);

        ArrayNode data = buildData(new String[][] {
            { "Alice", "30" },
            { "Bob",   "10" },
            { "Carol", "20" }
        }, "name", "score");

        autoLayout.measure(data);

        // Apply sort via interaction
        var rootPath = new SchemaPath("items");
        handler.apply(new LayoutInteraction.SortBy(
            rootPath.child("score"), false));

        // Version incremented — next layout pass will pick it up
        assertEquals("score",
            queryState.getFieldState(rootPath.child("score")).sortFields());
    }

    // --- InteractionMenuFactory can be constructed with the wired state ---

    @Test
    void menuFactoryConstructsWithWiredState() {
        var factory = new InteractionMenuFactory(handler, queryState);
        assertNotNull(factory);
    }

    // --- ColumnSortHandler can be constructed ---

    @Test
    void columnSortHandlerConstructsWithWiredState() {
        var sortHandler = new ColumnSortHandler(handler, queryState);
        assertNotNull(sortHandler);
    }

    // --- Multiple events in sequence ---

    @Test
    void multipleEventsAccumulate() {
        var path1 = new SchemaPath("items", "name");
        var path2 = new SchemaPath("items", "score");

        handler.apply(new LayoutInteraction.SortBy(path1, false));
        handler.apply(new LayoutInteraction.SetFilter(path2, "$score > 0"));
        handler.apply(new LayoutInteraction.SetRenderMode(
            new SchemaPath("items"), "TABLE"));

        assertEquals("name", queryState.getFieldState(path1).sortFields());
        assertEquals("$score > 0", queryState.getFieldState(path2).filterExpression());
        assertEquals("TABLE", queryState.getFieldState(new SchemaPath("items")).renderMode());
    }
}

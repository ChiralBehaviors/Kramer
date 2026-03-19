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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.chiralbehaviors.layout.ConfiguredMeasurementStrategy;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.style.Style;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests for InteractionMenuFactory and ColumnSortHandler sort-state cycling
 * (RDR-027 Phase 2a/2b). Non-JavaFX logic only.
 *
 * @author hhildebrand
 */
@ExtendWith(ApplicationExtension.class)
class InteractionMenuFactoryTest {

    private Style style;
    private LayoutQueryState queryState;
    private InteractionHandler handler;

    @Start
    void start(Stage stage) {
        stage.setScene(new Scene(new Pane(), 100, 100));
        stage.show();
    }

    @BeforeEach
    void setUp() {
        style = new Style(new ConfiguredMeasurementStrategy());
        queryState = new LayoutQueryState(style);
        handler = new InteractionHandler(queryState);
    }

    // --- Sort state cycling (ColumnSortHandler logic) ---

    @Test
    void sortCycleNoSortToAscending() {
        var path = new SchemaPath("items", "name");
        // No sort set — apply ascending
        handler.apply(new LayoutInteraction.SortBy(path, false));
        assertEquals("name", queryState.getFieldState(path).sortFields());
    }

    @Test
    void sortCycleAscendingToDescending() {
        var path = new SchemaPath("items", "name");
        handler.apply(new LayoutInteraction.SortBy(path, false)); // ascending
        assertEquals("name", queryState.getFieldState(path).sortFields());
        handler.apply(new LayoutInteraction.SortBy(path, true)); // descending
        assertEquals("-name", queryState.getFieldState(path).sortFields());
    }

    @Test
    void sortCycleClear() {
        var path = new SchemaPath("items", "name");
        handler.apply(new LayoutInteraction.SortBy(path, true)); // descending
        queryState.setSortFields(path, null); // clear
        assertNull(queryState.getFieldState(path).sortFields());
    }

    // --- Context menu construction (verifies menu items exist) ---
    // Note: ContextMenu requires JavaFX toolkit, so these tests verify
    // the factory can be constructed and the interaction handler dispatches correctly.

    @Test
    void factoryConstructsSuccessfully() {
        var factory = new InteractionMenuFactory(handler, queryState);
        assertNotNull(factory);
    }

    @Test
    void interactionHandlerProcessesAllEventTypes() {
        var path = new SchemaPath("items", "name");

        // Each event type should dispatch without error
        handler.apply(new LayoutInteraction.SortBy(path, false));
        handler.apply(new LayoutInteraction.SortBy(path, true));
        handler.apply(new LayoutInteraction.ToggleVisible(path));
        handler.apply(new LayoutInteraction.SetFilter(path, "$x > 5"));
        handler.apply(new LayoutInteraction.ClearFilter(path));
        handler.apply(new LayoutInteraction.SetRenderMode(path, "TABLE"));
        handler.apply(new LayoutInteraction.SetFormula(path, "$price * $qty"));
        handler.apply(new LayoutInteraction.ClearFormula(path));
        handler.apply(new LayoutInteraction.SetAggregate(path, "sum($x)"));
        handler.apply(new LayoutInteraction.ClearAggregate(path));
        handler.apply(new LayoutInteraction.ResetAll());

        // After ResetAll, everything should be cleared
        var fs = queryState.getFieldState(path);
        assertNull(fs.visible());
        assertNull(fs.filterExpression());
        assertNull(fs.formulaExpression());
        assertNull(fs.sortFields());
    }

    @Test
    void sortByDescendingProducesMinusPrefix() {
        var path = new SchemaPath("items", "score");
        handler.apply(new LayoutInteraction.SortBy(path, true));
        assertEquals("-score", queryState.getFieldState(path).sortFields());
    }

    @Test
    void sortByAscendingProducesPlainFieldName() {
        var path = new SchemaPath("items", "score");
        handler.apply(new LayoutInteraction.SortBy(path, false));
        assertEquals("score", queryState.getFieldState(path).sortFields());
    }

    // --- T2.1: ClearSort menu item ---

    @Test
    void primitiveMenuShowsClearSortWhenSorted() {
        var ref = new AtomicReference<List<String>>();
        Platform.runLater(() -> {
            var path = new SchemaPath("items", "name");
            handler.apply(new LayoutInteraction.SortBy(path, false));

            var factory = new InteractionMenuFactory(handler, queryState);
            ContextMenu menu = factory.buildPrimitiveMenu(path);
            ref.set(menu.getItems().stream()
                        .map(MenuItem::getText)
                        .filter(t -> t != null)
                        .toList());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get().contains("Clear sort"),
                   "Sorted field menu should contain 'Clear sort', got: " + ref.get());
    }

    @Test
    void primitiveMenuNoClearSortWhenUnsorted() {
        var ref = new AtomicReference<List<String>>();
        Platform.runLater(() -> {
            var path = new SchemaPath("items", "name");
            // No sort applied

            var factory = new InteractionMenuFactory(handler, queryState);
            ContextMenu menu = factory.buildPrimitiveMenu(path);
            ref.set(menu.getItems().stream()
                        .map(MenuItem::getText)
                        .filter(t -> t != null)
                        .toList());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(ref.get().contains("Clear sort"),
                    "Unsorted field menu should not contain 'Clear sort'");
    }

    // --- T2.1: SetAggregate/ClearAggregate menu items ---

    @Test
    void primitiveMenuShowsAggregateItem() {
        var ref = new AtomicReference<List<String>>();
        Platform.runLater(() -> {
            var path = new SchemaPath("items", "value");
            var factory = new InteractionMenuFactory(handler, queryState);
            ContextMenu menu = factory.buildPrimitiveMenu(path);
            ref.set(menu.getItems().stream()
                        .map(MenuItem::getText)
                        .filter(t -> t != null)
                        .toList());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get().contains("Aggregate..."),
                   "Primitive menu should contain 'Aggregate...', got: " + ref.get());
    }

    @Test
    void primitiveMenuShowsClearAggregateWhenSet() {
        var ref = new AtomicReference<List<String>>();
        Platform.runLater(() -> {
            var path = new SchemaPath("items", "value");
            handler.apply(new LayoutInteraction.SetAggregate(path, "sum($x)"));

            var factory = new InteractionMenuFactory(handler, queryState);
            ContextMenu menu = factory.buildPrimitiveMenu(path);
            ref.set(menu.getItems().stream()
                        .map(MenuItem::getText)
                        .filter(t -> t != null)
                        .toList());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get().contains("Clear aggregate"),
                   "Menu should contain 'Clear aggregate' when aggregate is set, got: " + ref.get());
    }

    @Test
    void primitiveMenuNoClearAggregateWhenUnset() {
        var ref = new AtomicReference<List<String>>();
        Platform.runLater(() -> {
            var path = new SchemaPath("items", "value");
            // No aggregate set

            var factory = new InteractionMenuFactory(handler, queryState);
            ContextMenu menu = factory.buildPrimitiveMenu(path);
            ref.set(menu.getItems().stream()
                        .map(MenuItem::getText)
                        .filter(t -> t != null)
                        .toList());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(ref.get().contains("Clear aggregate"),
                    "Menu should not contain 'Clear aggregate' when no aggregate set");
    }

    @Test
    void clearSortDispatchesClearSortEvent() {
        var path = new SchemaPath("items", "name");
        handler.apply(new LayoutInteraction.SortBy(path, false));
        assertEquals("name", queryState.getFieldState(path).sortFields());

        handler.apply(new LayoutInteraction.ClearSort(path));
        assertNull(queryState.getFieldState(path).sortFields(),
                   "ClearSort should null out sortFields");
    }

    // --- T2.2: Cell context menu — Filter by This Value + Copy Value ---

    @Test
    void cellMenuShowsFilterByThisValue() {
        var ref = new AtomicReference<List<String>>();
        Platform.runLater(() -> {
            var path = new SchemaPath("items", "name");
            var factory = new InteractionMenuFactory(handler, queryState);
            ContextMenu menu = factory.buildPrimitiveMenu(path, "Alice");
            ref.set(menu.getItems().stream()
                        .map(MenuItem::getText)
                        .filter(t -> t != null)
                        .toList());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get().stream().anyMatch(t -> t.contains("Filter by")),
                   "Cell menu should contain 'Filter by' item, got: " + ref.get());
        assertTrue(ref.get().contains("Copy value"),
                   "Cell menu should contain 'Copy value', got: " + ref.get());
    }

    @Test
    void cellMenuWithNullValueOmitsCellItems() {
        var ref = new AtomicReference<List<String>>();
        Platform.runLater(() -> {
            var path = new SchemaPath("items", "name");
            var factory = new InteractionMenuFactory(handler, queryState);
            ContextMenu menu = factory.buildPrimitiveMenu(path, null);
            ref.set(menu.getItems().stream()
                        .map(MenuItem::getText)
                        .filter(t -> t != null)
                        .toList());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(ref.get().stream().anyMatch(t -> t.contains("Filter by")),
                    "Null cell value should not produce 'Filter by' item");
        assertFalse(ref.get().contains("Copy value"),
                    "Null cell value should not produce 'Copy value' item");
    }

    @Test
    void filterByThisValueSetsFilterExpression() {
        var path = new SchemaPath("items", "name");
        handler.apply(new LayoutInteraction.SetFilter(path, "Alice"));
        assertEquals("Alice", queryState.getFieldState(path).filterExpression());
    }

    // --- T2.3: Quick-action aggregate items ---

    @Test
    void primitiveMenuShowsQuickAggregateItems() {
        var ref = new AtomicReference<List<String>>();
        Platform.runLater(() -> {
            var path = new SchemaPath("items", "value");
            var factory = new InteractionMenuFactory(handler, queryState);
            ContextMenu menu = factory.buildPrimitiveMenu(path);
            ref.set(menu.getItems().stream()
                        .map(MenuItem::getText)
                        .filter(t -> t != null)
                        .toList());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get().contains("Add SUM"),
                   "Primitive menu should contain 'Add SUM', got: " + ref.get());
        assertTrue(ref.get().contains("Add COUNT"),
                   "Primitive menu should contain 'Add COUNT', got: " + ref.get());
    }

    @Test
    void addSumSetsAggregateExpression() {
        var path = new SchemaPath("items", "value");
        handler.apply(new LayoutInteraction.SetAggregate(path, "sum($value)"));
        assertEquals("sum($value)", queryState.getFieldState(path).aggregateExpression());
    }

    @Test
    void addCountSetsAggregateExpression() {
        var path = new SchemaPath("items", "value");
        handler.apply(new LayoutInteraction.SetAggregate(path, "count($value)"));
        assertEquals("count($value)", queryState.getFieldState(path).aggregateExpression());
    }
}

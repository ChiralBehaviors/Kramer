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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.ConfiguredMeasurementStrategy;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.style.Style;

/**
 * Tests for InteractionMenuFactory and ColumnSortHandler sort-state cycling
 * (RDR-027 Phase 2a/2b). Non-JavaFX logic only.
 *
 * @author hhildebrand
 */
class InteractionMenuFactoryTest {

    private Style style;
    private LayoutQueryState queryState;
    private InteractionHandler handler;

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
}

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

/**
 * Tests for InteractionHandler (Kramer-x5l0).
 *
 * @author hhildebrand
 */
class InteractionHandlerTest {

    private LayoutQueryState queryState;
    private InteractionHandler handler;
    private SchemaPath path;

    @BeforeEach
    void setUp() {
        var style = new Style(new ConfiguredMeasurementStrategy());
        queryState = new LayoutQueryState(style);
        handler = new InteractionHandler(queryState);
        path = new SchemaPath("items", "name");
    }

    // --- SortBy ---

    @Test
    void sortByAscendingSetsFieldName() {
        handler.apply(new LayoutInteraction.SortBy(path, false));
        assertEquals("name", queryState.getFieldState(path).sortFields());
    }

    @Test
    void sortByDescendingSetsPrefixedFieldName() {
        handler.apply(new LayoutInteraction.SortBy(path, true));
        assertEquals("-name", queryState.getFieldState(path).sortFields());
    }

    // --- ToggleVisible ---

    @Test
    void toggleVisibleFalseWhenDefaultTrue() {
        // default is true (no override), toggling should set false
        handler.apply(new LayoutInteraction.ToggleVisible(path));
        assertFalse(queryState.getFieldState(path).visible());
    }

    @Test
    void toggleVisibleTrueWhenExplicitlyFalse() {
        queryState.setVisible(path, false);
        handler.apply(new LayoutInteraction.ToggleVisible(path));
        assertTrue(queryState.getFieldState(path).visible());
    }

    // --- SetFilter / ClearFilter ---

    @Test
    void setFilterStoresExpression() {
        handler.apply(new LayoutInteraction.SetFilter(path, "x > 0"));
        assertEquals("x > 0", queryState.getFieldState(path).filterExpression());
    }

    @Test
    void clearFilterRemovesExpression() {
        handler.apply(new LayoutInteraction.SetFilter(path, "x > 0"));
        handler.apply(new LayoutInteraction.ClearFilter(path));
        assertNull(queryState.getFieldState(path).filterExpression());
    }

    // --- SetRenderMode ---

    @Test
    void setRenderModeStoresMode() {
        handler.apply(new LayoutInteraction.SetRenderMode(path, "TABLE"));
        assertEquals("TABLE", queryState.getFieldState(path).renderMode());
    }

    // --- SetFormula / ClearFormula ---

    @Test
    void setFormulaStoresExpression() {
        handler.apply(new LayoutInteraction.SetFormula(path, "a + b"));
        assertEquals("a + b", queryState.getFieldState(path).formulaExpression());
    }

    @Test
    void clearFormulaRemovesExpression() {
        handler.apply(new LayoutInteraction.SetFormula(path, "a + b"));
        handler.apply(new LayoutInteraction.ClearFormula(path));
        assertNull(queryState.getFieldState(path).formulaExpression());
    }

    // --- SetAggregate / ClearAggregate ---

    @Test
    void setAggregateStoresExpression() {
        handler.apply(new LayoutInteraction.SetAggregate(path, "sum(x)"));
        assertEquals("sum(x)", queryState.getFieldState(path).aggregateExpression());
    }

    @Test
    void clearAggregateRemovesExpression() {
        handler.apply(new LayoutInteraction.SetAggregate(path, "sum(x)"));
        handler.apply(new LayoutInteraction.ClearAggregate(path));
        assertNull(queryState.getFieldState(path).aggregateExpression());
    }

    // --- ResetAll ---

    @Test
    void resetAllClearsAllOverrides() {
        handler.apply(new LayoutInteraction.SetFilter(path, "x > 0"));
        handler.apply(new LayoutInteraction.SetFormula(path, "a + b"));
        handler.apply(new LayoutInteraction.SetAggregate(path, "sum(x)"));

        handler.apply(new LayoutInteraction.ResetAll(null));

        assertEquals(FieldState.EMPTY, queryState.getFieldState(path));
    }

    @Test
    void resetAllWithNullPathDoesNotNPE() {
        assertDoesNotThrow(() -> handler.apply(new LayoutInteraction.ResetAll()));
    }

    // --- Change notification ---

    @Test
    void eachEventFiresExactlyOneChangeNotification() {
        var counter = new AtomicInteger(0);
        queryState.addChangeListener(counter::incrementAndGet);

        handler.apply(new LayoutInteraction.SortBy(path, false));
        assertEquals(1, counter.get());

        handler.apply(new LayoutInteraction.ToggleVisible(path));
        assertEquals(2, counter.get());

        handler.apply(new LayoutInteraction.SetFilter(path, "x > 0"));
        assertEquals(3, counter.get());

        handler.apply(new LayoutInteraction.ClearFilter(path));
        assertEquals(4, counter.get());

        handler.apply(new LayoutInteraction.SetRenderMode(path, "TABLE"));
        assertEquals(5, counter.get());

        handler.apply(new LayoutInteraction.SetFormula(path, "a + b"));
        assertEquals(6, counter.get());

        handler.apply(new LayoutInteraction.ClearFormula(path));
        assertEquals(7, counter.get());

        handler.apply(new LayoutInteraction.SetAggregate(path, "sum(x)"));
        assertEquals(8, counter.get());

        handler.apply(new LayoutInteraction.ClearAggregate(path));
        assertEquals(9, counter.get());

        handler.apply(new LayoutInteraction.ResetAll(null));
        assertEquals(10, counter.get());
    }
}

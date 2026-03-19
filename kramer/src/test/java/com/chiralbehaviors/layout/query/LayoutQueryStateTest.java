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
import com.chiralbehaviors.layout.LayoutPropertyKeys;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.style.Style;

/**
 * Tests for LayoutQueryState (RDR-026 Phase 1).
 *
 * @author hhildebrand
 */
class LayoutQueryStateTest {

    private Style style;
    private LayoutQueryState queryState;
    private SchemaPath path;

    @BeforeEach
    void setUp() {
        style = new Style(new ConfiguredMeasurementStrategy());
        queryState = new LayoutQueryState(style);
        path = new SchemaPath("items", "name");
    }

    // --- FieldState construction ---

    @Test
    void fieldStateAllNullsByDefault() {
        var fs = new FieldState(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertNull(fs.visible());
        assertNull(fs.renderMode());
        assertNull(fs.hideIfEmpty());
        assertNull(fs.sortFields());
        assertNull(fs.filterExpression());
        assertNull(fs.formulaExpression());
        assertNull(fs.aggregateExpression());
        assertNull(fs.sortExpression());
        assertNull(fs.pivotField());
        assertNull(fs.frozen());
        assertNull(fs.aggregatePosition());
        assertNull(fs.cellFormat());
        assertNull(fs.columnWidth());
        assertNull(fs.collapseDuplicates());
    }

    @Test
    void fieldStateWithValues() {
        var fs = new FieldState(true, "TABLE", false, "name", "$x > 5",
            "$price * $qty", "sum($revenue)", "$score", "category", true, "footer", "%.2f", 150.0, true);
        assertEquals(true, fs.visible());
        assertEquals("TABLE", fs.renderMode());
        assertEquals(false, fs.hideIfEmpty());
        assertEquals("name", fs.sortFields());
        assertEquals("$x > 5", fs.filterExpression());
        assertEquals("$price * $qty", fs.formulaExpression());
        assertEquals("sum($revenue)", fs.aggregateExpression());
        assertEquals("$score", fs.sortExpression());
        assertEquals("category", fs.pivotField());
        assertEquals(true, fs.frozen());
        assertEquals("footer", fs.aggregatePosition());
        assertEquals("%.2f", fs.cellFormat());
        assertEquals(150.0, fs.columnWidth());
        assertEquals(true, fs.collapseDuplicates());
    }

    // --- getFieldState returns all-null for unset path ---

    @Test
    void getFieldStateReturnsAllNullForUnsetPath() {
        var fs = queryState.getFieldState(path);
        assertNotNull(fs);
        assertNull(fs.visible());
        assertNull(fs.renderMode());
        assertNull(fs.hideIfEmpty());
        assertNull(fs.sortFields());
        assertNull(fs.filterExpression());
        assertNull(fs.formulaExpression());
        assertNull(fs.aggregateExpression());
        assertNull(fs.sortExpression());
        assertNull(fs.pivotField());
    }

    // --- Typed setter round-trips ---

    @Test
    void setVisibleRoundTrip() {
        queryState.setVisible(path, false);
        assertEquals(false, queryState.getFieldState(path).visible());
        assertEquals(false, queryState.getBoolean(path, LayoutPropertyKeys.VISIBLE, true));
    }

    @Test
    void setRenderModeRoundTrip() {
        queryState.setRenderMode(path, "SPARKLINE");
        assertEquals("SPARKLINE", queryState.getFieldState(path).renderMode());
        assertEquals("SPARKLINE", queryState.getString(path, LayoutPropertyKeys.RENDER_MODE, null));
    }

    @Test
    void setHideIfEmptyRoundTrip() {
        queryState.setHideIfEmpty(path, true);
        assertEquals(true, queryState.getFieldState(path).hideIfEmpty());
        assertEquals(true, queryState.getBoolean(path, LayoutPropertyKeys.HIDE_IF_EMPTY, false));
    }

    @Test
    void setSortFieldsRoundTrip() {
        queryState.setSortFields(path, "name,-score");
        assertEquals("name,-score", queryState.getFieldState(path).sortFields());
        assertEquals("name,-score", queryState.getString(path, LayoutPropertyKeys.SORT_FIELDS, ""));
    }

    @Test
    void setFilterExpressionRoundTrip() {
        queryState.setFilterExpression(path, "$revenue > 50");
        assertEquals("$revenue > 50", queryState.getFieldState(path).filterExpression());
        assertEquals("$revenue > 50", queryState.getString(path, LayoutPropertyKeys.FILTER_EXPRESSION, null));
    }

    @Test
    void setFormulaExpressionRoundTrip() {
        queryState.setFormulaExpression(path, "$price * $qty");
        assertEquals("$price * $qty", queryState.getFieldState(path).formulaExpression());
        assertEquals("$price * $qty", queryState.getString(path, LayoutPropertyKeys.FORMULA_EXPRESSION, null));
    }

    @Test
    void setAggregateExpressionRoundTrip() {
        queryState.setAggregateExpression(path, "sum($revenue)");
        assertEquals("sum($revenue)", queryState.getFieldState(path).aggregateExpression());
        assertEquals("sum($revenue)", queryState.getString(path, LayoutPropertyKeys.AGGREGATE_EXPRESSION, null));
    }

    @Test
    void setSortExpressionRoundTrip() {
        queryState.setSortExpression(path, "abs($delta)");
        assertEquals("abs($delta)", queryState.getFieldState(path).sortExpression());
        assertEquals("abs($delta)", queryState.getString(path, LayoutPropertyKeys.SORT_EXPRESSION, null));
    }

    @Test
    void setPivotFieldRoundTrip() {
        queryState.setPivotField(path, "category");
        assertEquals("category", queryState.getFieldState(path).pivotField());
        assertEquals("category", queryState.getString(path, "pivot-field", ""));
    }

    // --- getVisibleOrDefault ---

    @Test
    void getVisibleOrDefaultReturnsTrueWhenUnset() {
        assertTrue(queryState.getVisibleOrDefault(path));
    }

    @Test
    void getVisibleOrDefaultReturnsFalseWhenSetFalse() {
        queryState.setVisible(path, false);
        assertFalse(queryState.getVisibleOrDefault(path));
    }

    @Test
    void getVisibleOrDefaultReturnsTrueWhenSetTrue() {
        queryState.setVisible(path, true);
        assertTrue(queryState.getVisibleOrDefault(path));
    }

    // --- Version tracking ---

    @Test
    void versionIncrementsOnMutation() {
        long v0 = queryState.getVersion();
        queryState.setVisible(path, false);
        long v1 = queryState.getVersion();
        assertTrue(v1 > v0);
    }

    @Test
    void versionIncrementsOnReset() {
        queryState.setVisible(path, false);
        long v1 = queryState.getVersion();
        queryState.reset();
        long v2 = queryState.getVersion();
        assertTrue(v2 > v1);
    }

    // --- Change listener ---

    @Test
    void changeListenerFiresOnMutation() {
        var count = new AtomicInteger(0);
        queryState.addChangeListener(count::incrementAndGet);
        queryState.setVisible(path, false);
        assertEquals(1, count.get());
    }

    @Test
    void changeListenerFiresOnReset() {
        var count = new AtomicInteger(0);
        queryState.setVisible(path, false);
        queryState.addChangeListener(count::incrementAndGet);
        queryState.reset();
        assertEquals(1, count.get());
    }

    @Test
    void multipleListenersAllFire() {
        var count1 = new AtomicInteger(0);
        var count2 = new AtomicInteger(0);
        queryState.addChangeListener(count1::incrementAndGet);
        queryState.addChangeListener(count2::incrementAndGet);
        queryState.setVisible(path, false);
        assertEquals(1, count1.get());
        assertEquals(1, count2.get());
    }

    @Test
    void removeChangeListenerStopsFiring() {
        var count = new AtomicInteger(0);
        Runnable listener = count::incrementAndGet;
        queryState.addChangeListener(listener);
        queryState.setVisible(path, false);
        assertEquals(1, count.get());
        queryState.removeChangeListener(listener);
        queryState.setVisible(path, true);
        assertEquals(1, count.get()); // no increment after removal
    }

    // --- suppressNotifications ---

    @Test
    void suppressNotificationsFiresOnceAfterBatch() {
        var count = new AtomicInteger(0);
        queryState.addChangeListener(count::incrementAndGet);
        queryState.suppressNotifications(() -> {
            queryState.setVisible(path, false);
            queryState.setFilterExpression(path, "$x > 5");
            queryState.setSortFields(path, "name");
        });
        assertEquals(1, count.get(), "Should fire exactly once after batch");
    }

    @Test
    void suppressNotificationsFiresNothingDuringBatch() {
        var duringCount = new AtomicInteger(0);
        queryState.addChangeListener(() -> {
            // If this fires during suppression, duringCount increments before
            // the batch completes — we check it hasn't fired prematurely
            duringCount.incrementAndGet();
        });
        queryState.suppressNotifications(() -> {
            queryState.setVisible(path, false);
            // At this point, listener should NOT have fired yet
            queryState.setFilterExpression(path, "$x > 5");
        });
        // After batch: exactly 1 fire
        assertEquals(1, duringCount.get());
    }

    @Test
    void suppressNotificationsNoFireIfNoMutations() {
        var count = new AtomicInteger(0);
        queryState.addChangeListener(count::incrementAndGet);
        queryState.suppressNotifications(() -> {
            // no mutations
        });
        assertEquals(0, count.get(), "No mutations means no notification");
    }

    // --- Reset ---

    @Test
    void resetClearsAllOverrides() {
        queryState.setVisible(path, false);
        queryState.setFilterExpression(path, "$x > 5");
        var otherPath = new SchemaPath("items", "score");
        queryState.setSortExpression(otherPath, "$score");

        queryState.reset();

        var fs = queryState.getFieldState(path);
        assertNull(fs.visible());
        assertNull(fs.filterExpression());
        var fs2 = queryState.getFieldState(otherPath);
        assertNull(fs2.sortExpression());
    }

    // --- LayoutStylesheet delegation ---

    @Test
    void delegatesGetDoubleToInnerStylesheet() {
        // No override set — should return default
        assertEquals(42.0, queryState.getDouble(path, "some-double", 42.0));
    }

    @Test
    void delegatesGetIntToInnerStylesheet() {
        assertEquals(7, queryState.getInt(path, "some-int", 7));
    }

    @Test
    void delegatesGetStringToInnerStylesheet() {
        assertEquals("default", queryState.getString(path, "unknown-key", "default"));
    }

    @Test
    void delegatesGetBooleanToInnerStylesheet() {
        // visible not set → default true
        assertTrue(queryState.getBoolean(path, LayoutPropertyKeys.VISIBLE, true));
    }

    // --- Setting null clears the override ---

    @Test
    void setNullClearsOverride() {
        queryState.setFilterExpression(path, "$x > 5");
        assertEquals("$x > 5", queryState.getFieldState(path).filterExpression());
        queryState.setFilterExpression(path, null);
        assertNull(queryState.getFieldState(path).filterExpression());
    }

    // --- LayoutStylesheet implementation ---

    @Test
    void implementsLayoutStylesheet() {
        assertInstanceOf(com.chiralbehaviors.layout.LayoutStylesheet.class, queryState);
    }
}

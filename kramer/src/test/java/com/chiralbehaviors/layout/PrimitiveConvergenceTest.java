// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for Kramer-16k: convergence detection in PrimitiveLayout.
 *
 * When p90Width is stable (delta < epsilon) for k consecutive measure() calls
 * and sampleCount >= minSamples, the MeasureResult is frozen and subsequent
 * calls return the cached column width without re-measuring.
 */
class PrimitiveConvergenceTest {

    private static final int    DEFAULT_MIN_SAMPLES = 30;
    private static final double DEFAULT_EPSILON     = 1.0;
    private static final int    DEFAULT_K           = 3;

    // --- helpers ---

    /**
     * Build a variable-length dataset of {@code count} elements: 90% short (1 char),
     * 10% long (20 chars). charWidth=1.0, so widths equal char counts.
     */
    private static ArrayNode buildVariableDataset(int count) {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        int longCount = Math.max(1, count / 10);
        int shortCount = count - longCount;
        for (int i = 0; i < shortCount; i++) {
            arr.add("x");           // width 1.0
        }
        for (int i = 0; i < longCount; i++) {
            arr.add("x".repeat(20)); // width 20.0
        }
        return arr;
    }

    /**
     * Create a Style mock whose getStylesheet() returns a DefaultLayoutStylesheet
     * with default values (no overrides).
     */
    private static Style modelWithDefaultStylesheet() {
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(sheet);
        return model;
    }

    /**
     * Create a Style mock whose getStylesheet() returns the given stylesheet.
     */
    private static Style modelWith(LayoutStylesheet sheet) {
        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(sheet);
        return model;
    }

    // --- Test 1: convergence NOT triggered below minSamples ---

    @Test
    void noConvergenceBelowMinSamples() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        layout.setSchemaPath(new SchemaPath("text"));
        Style model = modelWithDefaultStylesheet();

        // 5 elements — far below minSamples=30
        ArrayNode small = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 5; i++) {
            small.add("x");
        }

        // Call measure() many times — should never converge
        for (int i = 0; i < DEFAULT_K + 2; i++) {
            layout.measure(small, n -> n, model);
        }

        // contentStats is null for sampleCount < 30, so convergence cannot fire
        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result);
        assertNull(result.contentStats(), "contentStats must be null below minSamples");
        // frozenResult should not be set
        assertFalse(layout.isConverged(), "Must not converge below minSamples");
    }

    // --- Test 2: convergence triggered when p90Width stable for k consecutive calls ---

    @Test
    void convergenceTriggeredAfterKStableCalls() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        layout.setSchemaPath(new SchemaPath("text"));
        Style model = modelWithDefaultStylesheet();

        // Stable dataset: 35 elements, same composition every call
        ArrayNode data = buildVariableDataset(35);

        // Call (k-1) times — not yet converged
        for (int i = 0; i < DEFAULT_K - 1; i++) {
            layout.measure(data, n -> n, model);
            assertFalse(layout.isConverged(),
                "Should not converge after only " + (i + 1) + " stable call(s)");
        }

        // k-th stable call — now converged
        layout.measure(data, n -> n, model);
        assertTrue(layout.isConverged(), "Should converge after k=" + DEFAULT_K + " stable calls");
    }

    // --- Test 3: converged=true in ContentWidthStats after convergence ---

    @Test
    void contentStatsConvergedTrueAfterConvergence() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        layout.setSchemaPath(new SchemaPath("text"));
        Style model = modelWithDefaultStylesheet();

        ArrayNode data = buildVariableDataset(35);

        // Drive to convergence
        for (int i = 0; i < DEFAULT_K; i++) {
            layout.measure(data, n -> n, model);
        }

        assertTrue(layout.isConverged());
        ContentWidthStats stats = layout.getMeasureResult().contentStats();
        assertNotNull(stats);
        assertTrue(stats.converged(), "ContentWidthStats.converged must be true after convergence");
    }

    // --- Test 4: frozenResult returned on subsequent measure() calls (short-circuit) ---

    @Test
    void frozenResultReturnedOnSubsequentMeasureCalls() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        layout.setSchemaPath(new SchemaPath("text"));
        Style model = modelWithDefaultStylesheet();

        ArrayNode data = buildVariableDataset(35);

        // Drive to convergence
        for (int i = 0; i < DEFAULT_K; i++) {
            layout.measure(data, n -> n, model);
        }
        assertTrue(layout.isConverged());
        double frozenColumnWidth = layout.columnWidth();

        // After convergence, different data should NOT change the result
        ArrayNode differentData = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 35; i++) {
            differentData.add("x".repeat(50)); // much wider
        }

        double result = layout.measure(differentData, n -> n, model);

        // Must return frozen width, not the width of the new data
        assertEquals(frozenColumnWidth, result, 1e-6,
            "measure() must return frozen column width after convergence");
        assertEquals(frozenColumnWidth, layout.columnWidth(), 1e-6,
            "columnWidth() must reflect frozen result after convergence");
    }

    // --- Test 5: frozenResult invalidated when stylesheet version changes ---

    @Test
    void frozenResultInvalidatedOnStylesheetVersionChange() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        layout.setSchemaPath(new SchemaPath("text"));
        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        Style model = modelWith(sheet);

        ArrayNode data = buildVariableDataset(35);

        // Drive to convergence
        for (int i = 0; i < DEFAULT_K; i++) {
            layout.measure(data, n -> n, model);
        }
        assertTrue(layout.isConverged());

        // Change the stylesheet version
        sheet.setOverride(new SchemaPath("text"), "stat-min-samples", 30);

        // Now measure with different data should re-run (frozen is stale)
        // Build wider data so result differs if re-measured
        ArrayNode wideData = JsonNodeFactory.instance.arrayNode();
        int longCount = 4;
        int shortCount = 35 - longCount;
        for (int i = 0; i < shortCount; i++) {
            wideData.add("x".repeat(5));
        }
        for (int i = 0; i < longCount; i++) {
            wideData.add("x".repeat(100));
        }

        layout.measure(wideData, n -> n, model);

        // After version change, convergence state should be reset
        assertFalse(layout.isConverged(),
            "Convergence must be invalidated when stylesheet version changes");
    }

    // --- Test 6: LayoutStylesheet overrides for stat-min-samples honored ---

    @Test
    void customMinSamplesOverrideHonored() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        SchemaPath path = new SchemaPath("text");
        layout.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        // Reduce minSamples to 5 so our small dataset qualifies
        sheet.setOverride(path, "stat-min-samples", 5);
        // Also set k=2 to speed up convergence
        sheet.setOverride(path, "stat-convergence-k", 2);
        Style model = modelWith(sheet);

        // Dataset with 6 elements (>= custom minSamples=5)
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 5; i++) {
            data.add("x");
        }
        data.add("x".repeat(20));

        // With custom minSamples=5 and k=2, should converge in 2 stable calls
        layout.measure(data, n -> n, model);
        assertFalse(layout.isConverged(), "Not converged after 1 stable call (k=2)");

        layout.measure(data, n -> n, model);
        assertTrue(layout.isConverged(),
            "Should converge after 2 stable calls with custom k=2 and minSamples=5");
    }

    // --- Test 7: stat-convergence-epsilon override honored ---

    @Test
    void customEpsilonOverrideHonored() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        SchemaPath path = new SchemaPath("text");
        layout.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        // Very tight epsilon: requires exact match
        sheet.setOverride(path, "stat-convergence-epsilon", 0.001);
        sheet.setOverride(path, "stat-convergence-k", 2);
        sheet.setOverride(path, "stat-min-samples", 5);
        Style model = modelWith(sheet);

        // Two slightly different datasets that differ by ~0.5 in p90
        // With epsilon=0.001 this should NOT converge
        ArrayNode data1 = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 5; i++) {
            data1.add("x");
        }
        data1.add("x".repeat(10));

        ArrayNode data2 = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 5; i++) {
            data2.add("x");
        }
        data2.add("x".repeat(15)); // different p90

        layout.measure(data1, n -> n, model);
        layout.measure(data2, n -> n, model);

        assertFalse(layout.isConverged(),
            "Should NOT converge when p90 delta exceeds tight epsilon=0.001");
    }

    // --- Test 8: stat-convergence-k override honored ---

    @Test
    void customKOverrideHonored() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        SchemaPath path = new SchemaPath("text");
        layout.setSchemaPath(path);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        sheet.setOverride(path, "stat-min-samples", 5);
        sheet.setOverride(path, "stat-convergence-k", 5); // require 5 stable calls
        Style model = modelWith(sheet);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 5; i++) {
            data.add("x");
        }
        data.add("x".repeat(20));

        // 4 stable calls should not converge with k=5
        for (int i = 0; i < 4; i++) {
            layout.measure(data, n -> n, model);
            assertFalse(layout.isConverged(),
                "Should not converge after " + (i + 1) + " stable calls with k=5");
        }

        // 5th stable call triggers convergence
        layout.measure(data, n -> n, model);
        assertTrue(layout.isConverged(), "Should converge after 5 stable calls with k=5");
    }
}

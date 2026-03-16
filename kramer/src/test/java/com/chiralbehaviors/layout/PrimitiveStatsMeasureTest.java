// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for Kramer-ufx: p50/p90 percentile computation in PrimitiveLayout.measure().
 *
 * Phase 1: minSamples = 30.
 * - sampleCount >= 30: ContentWidthStats populated; p90 used as effectiveWidth for variable-length.
 * - sampleCount < 30: contentStats null; averageWidth used (existing behaviour).
 * - isVariableLength == false: maxWidth used regardless of sampleCount.
 * - empty dataset: contentStats null.
 */
class PrimitiveStatsMeasureTest {

    private static final int MIN_SAMPLES = 30;

    // --- Helper: build array of text values with given char widths --

    /**
     * Adds {@code count} text values of exactly {@code charCount} characters each.
     */
    private static void addFixed(ArrayNode arr, int count, int charCount) {
        String value = "x".repeat(charCount);
        for (int i = 0; i < count; i++) {
            arr.add(value);
        }
    }

    /**
     * Builds an array of 35 strings: 30 short (1 char) and 5 very long (20 chars).
     * charWidth=1.0 so widths equal char counts.
     * maxWidth=20, averageWidth ~ (30*1 + 5*20)/35 = 130/35 ≈ 3.71
     * maxWidth/averageWidth ≈ 5.4 > variableLengthThreshold(2.0) → isVariableLength=true
     */
    private static ArrayNode buildVariableDataset() {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        addFixed(arr, 30, 1);  // 30 short
        addFixed(arr, 5, 20);  // 5 long
        return arr;
    }

    /**
     * Builds an array of 35 strings all the same length (10 chars each).
     * maxWidth/averageWidth = 1.0 < variableLengthThreshold(2.0) → isVariableLength=false
     */
    private static ArrayNode buildFixedDataset() {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        addFixed(arr, 35, 10);
        return arr;
    }

    // --- Test 1: contentStats populated for dataset >= minSamples ---

    @Test
    void contentStatsPopulatedWhenSampleCountAtLeastMinSamples() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        Style model = mock(Style.class);

        ArrayNode data = buildVariableDataset(); // 35 elements
        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result.contentStats(),
            "contentStats must be populated when sampleCount >= " + MIN_SAMPLES);
        assertEquals(35, result.contentStats().sampleCount(),
            "sampleCount should equal dataset size");
    }

    // --- Test 2: p50 and p90 are correctly computed ---

    @Test
    void p50AndP90AreCorrectlyComputed() {
        // 35 values: widths [1,1,...1 (30 times), 20,20,...20 (5 times)]
        // sorted: [1,1,...1,20,...20]  (charWidth=1.0)
        // p50 index = 35/2 = 17 → value 1.0
        // p90 index = 35*9/10 = 31 → value 20.0
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        Style model = mock(Style.class);

        ArrayNode data = buildVariableDataset();
        layout.measure(data, n -> n, model);

        ContentWidthStats stats = layout.getMeasureResult().contentStats();
        assertNotNull(stats);
        assertEquals(1.0, stats.p50Width(), 1e-6, "p50 should be the median (1.0)");
        assertEquals(20.0, stats.p90Width(), 1e-6, "p90 should be the 90th percentile (20.0)");
    }

    // --- Test 3: p90 used as effectiveWidth for variable-length (replaces averageWidth) ---

    @Test
    void p90UsedAsEffectiveWidthForVariableLengthWithSufficientSamples() {
        // isVariableLength=true, sampleCount=35 (>= 30)
        // effective width should be p90 (= 20.0), not averageWidth (~3.71)
        // dataWidth = max(defaultWidth=0, p90=20.0) = 20.0
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        Style model = mock(Style.class);

        ArrayNode data = buildVariableDataset();
        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertTrue(result.isVariableLength(), "Dataset should be variable-length");
        // dataWidth == p90 == 20.0 (snapped; snap is identity in tests)
        assertEquals(20.0, result.dataWidth(), 1e-6,
            "dataWidth should equal p90 for variable-length with >= 30 samples");
    }

    // --- Test 4: maxWidth retained for fixed-length columns ---

    @Test
    void maxWidthRetainedForFixedLengthColumns() {
        // All 35 values are 10 chars → maxWidth=10, averageWidth=10
        // maxWidth/averageWidth = 1.0 < 2.0 → isVariableLength=false
        // effectiveWidth = maxWidth (unchanged)
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("timestamp"), style);
        Style model = mock(Style.class);

        ArrayNode data = buildFixedDataset();
        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertFalse(result.isVariableLength(), "Uniform dataset should be fixed-length");
        assertEquals(10.0, result.maxWidth(), 1e-6, "maxWidth should be 10.0");
        assertEquals(10.0, result.dataWidth(), 1e-6,
            "Fixed-length should use maxWidth, not p90");
    }

    // --- Test 5: dataset < minSamples uses existing averageWidth behaviour ---

    @Test
    void smallDatasetUsesAverageWidthBehaviourAndNullContentStats() {
        // 5 elements: widths [1,1,1,1,20] (charWidth=1.0)
        // maxWidth=20, averageWidth=(4+20)/5=4.8
        // maxWidth/averageWidth ≈ 4.17 > 2.0 → isVariableLength=true
        // sampleCount=5 < 30 → contentStats=null, effectiveWidth=averageWidth=4.8
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("short"), style);
        Style model = mock(Style.class);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        addFixed(data, 4, 1);
        addFixed(data, 1, 20);

        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNull(result.contentStats(),
            "contentStats must be null when sampleCount < " + MIN_SAMPLES);
        assertTrue(result.isVariableLength());
        // effectiveWidth = averageWidth = (4*1 + 20) / 5 = 4.8, snap(4.8) = 5.0
        assertEquals(5.0, result.dataWidth(), 1e-6,
            "Small dataset should use averageWidth (snapped) as effectiveWidth");
    }

    // --- Test 6: empty dataset produces null contentStats ---

    @Test
    void emptyDatasetProducesNullContentStats() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("empty"), style);
        Style model = mock(Style.class);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNull(result.contentStats(), "Empty dataset should produce null contentStats");
    }

    // --- Test 7: converged=false for Phase 1 ---

    @Test
    void contentStatsConvergedIsFalsePhase1() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("text"), style);
        Style model = mock(Style.class);

        ArrayNode data = buildVariableDataset();
        layout.measure(data, n -> n, model);

        ContentWidthStats stats = layout.getMeasureResult().contentStats();
        assertNotNull(stats);
        assertFalse(stats.converged(), "converged=false because consecutiveStableCount (1) < k (3) on first call");
    }

    // --- Test 8: exactly minSamples elements triggers stats ---

    @Test
    void exactlyMinSamplesTriggersContentStats() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(1.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("boundary"), style);
        Style model = mock(Style.class);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // 30 variable-length values: 20 short (1 char), 10 long (10 chars)
        // maxWidth=10, averageWidth=(20+100)/30 = 4.0
        // maxWidth/averageWidth = 2.5 > 2.0 → isVariableLength=true
        addFixed(data, 20, 1);
        addFixed(data, 10, 10);

        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result.contentStats(),
            "contentStats must be populated at exactly minSamples=" + MIN_SAMPLES);
        assertEquals(30, result.contentStats().sampleCount());
    }
}

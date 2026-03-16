// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class NumericStatsTest {

    // --- NumericStats record construction ---

    @Test
    void numericStatsRecordConstruction() {
        NumericStats stats = new NumericStats(1.5, 99.9);
        assertEquals(1.5, stats.numericMin(), 1e-9);
        assertEquals(99.9, stats.numericMax(), 1e-9);
    }

    @Test
    void numericStatsEquality() {
        NumericStats a = new NumericStats(0.0, 100.0);
        NumericStats b = new NumericStats(0.0, 100.0);
        NumericStats c = new NumericStats(1.0, 100.0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    // --- MeasureResult with non-null numericStats ---

    @Test
    void measureResultAcceptsNonNullNumericStats() {
        NumericStats ns = new NumericStats(5.0, 50.0);
        MeasureResult result = new MeasureResult(
            10.0, 20.0, 15.0, 25.0,
            1, false,
            0, 0,
            null, List.of(),
            null, ns, null
        );
        assertNotNull(result.numericStats());
        assertEquals(5.0, result.numericStats().numericMin(), 1e-9);
        assertEquals(50.0, result.numericStats().numericMax(), 1e-9);
    }

    @Test
    void measureResultAcceptsNullNumericStats() {
        MeasureResult result = new MeasureResult(
            10.0, 20.0, 15.0, 25.0,
            1, false,
            0, 0,
            null, List.of(),
            null, null, null
        );
        assertNull(result.numericStats());
    }

    // --- All-numeric data produces numericStats with correct min/max ---

    @Test
    void allNumericDataProducesNumericStatsWithMinMax() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("value"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= 40; i++) {
            data.add(i * 10.0);
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result);
        assertNotNull(result.numericStats(), "All-numeric data must produce non-null numericStats");
        assertEquals(10.0, result.numericStats().numericMin(), 1e-9,
                     "numericMin should be the smallest value");
        assertEquals(400.0, result.numericStats().numericMax(), 1e-9,
                     "numericMax should be the largest value");
    }

    // --- Mixed numeric/text data produces null numericStats ---

    @Test
    void mixedDataProducesNullNumericStats() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("value"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 35; i++) {
            if (i % 5 == 0) {
                data.add("text_value_" + i);
            } else {
                data.add(i * 3.0);
            }
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result);
        assertNull(result.numericStats(), "Mixed numeric/text data must produce null numericStats");
    }

    // --- renderMode set to BAR when all numeric ---

    @Test
    void allNumericDataSetsRenderModeBAR() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("score"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= 40; i++) {
            data.add(i * 2.5);
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BAR, layout.getRenderMode(),
                     "renderMode should be BAR when all data is numeric");
    }

    @Test
    void mixedDataKeepsRenderModeTEXT() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("label"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 35; i++) {
            if (i % 4 == 0) {
                data.add("non-numeric");
            } else {
                data.add(i * 1.0);
            }
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.TEXT, layout.getRenderMode(),
                     "renderMode should remain TEXT when data contains non-numeric values");
    }

    // --- Double.NEGATIVE_INFINITY / POSITIVE_INFINITY init produces correct range ---

    @Test
    void infinityInitProducesCorrectRange() {
        // Verify that initializing min=+INF, max=-INF then updating with a single value yields [v, v]
        double initMin = Double.POSITIVE_INFINITY;
        double initMax = Double.NEGATIVE_INFINITY;
        double v = 42.0;
        double min = Math.min(initMin, v);
        double max = Math.max(initMax, v);
        assertEquals(42.0, min, 1e-9);
        assertEquals(42.0, max, 1e-9);
    }

    @Test
    void allNumericSingleValueRangeIsMinEqualsMax() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("count"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 35; i++) {
            data.add(7.0);
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result.numericStats());
        assertEquals(7.0, result.numericStats().numericMin(), 1e-9);
        assertEquals(7.0, result.numericStats().numericMax(), 1e-9);
    }

    // --- Below MIN_SAMPLES: numericStats still computed (all-numeric detection is separate) ---

    @Test
    void belowMinSamplesAllNumericStillDetected() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("val"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // Only 2 elements — below MIN_SAMPLES=30, but still all numeric
        data.add(1.0);
        data.add(9.0);

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result);
        // numericStats should be non-null even with < MIN_SAMPLES because numeric detection is independent
        assertNotNull(result.numericStats(),
                      "numericStats should be set for all-numeric data regardless of sample count");
        assertEquals(1.0, result.numericStats().numericMin(), 1e-9);
        assertEquals(9.0, result.numericStats().numericMax(), 1e-9);
    }
}

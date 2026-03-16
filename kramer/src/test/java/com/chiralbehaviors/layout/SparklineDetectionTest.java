// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for Kramer-l6d: Bifurcate array-vs-scalar detection in PrimitiveLayout.measure().
 * Verifies that scalar numeric → BAR, array-of-numbers → SPARKLINE, etc.
 */
class SparklineDetectionTest {

    // ---- Scalar numeric → BAR (unchanged behaviour) ----

    @Test
    void scalarNumericValuesProduceBarMode() {
        var style = TestLayouts.mockPrimitiveStyle(7.0);
        var layout = new PrimitiveLayout(new Primitive("score"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add(10.0);
        data.add(20.0);
        data.add(30.0);
        data.add(40.0);

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.BAR, layout.getRenderMode(),
                     "Scalar numeric values must produce BAR, not SPARKLINE");
    }

    // ---- Array-of-numbers → SPARKLINE ----

    @Test
    void arrayOfNumbersProducesSparklineMode() {
        var style = TestLayouts.mockPrimitiveStyle(7.0);
        var layout = new PrimitiveLayout(new Primitive("series"), style);

        // Each row is an array of numbers (time-series per record)
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ArrayNode row1 = JsonNodeFactory.instance.arrayNode();
        row1.add(1.0); row1.add(2.0); row1.add(3.0);
        ArrayNode row2 = JsonNodeFactory.instance.arrayNode();
        row2.add(4.0); row2.add(5.0); row2.add(6.0);
        data.add(row1);
        data.add(row2);

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode(),
                     "Array-of-numbers must produce SPARKLINE");
    }

    // ---- Array of strings → TEXT ----

    @Test
    void arrayOfStringsProducesTextMode() {
        var style = TestLayouts.mockPrimitiveStyle(7.0);
        var layout = new PrimitiveLayout(new Primitive("tags"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ArrayNode row1 = JsonNodeFactory.instance.arrayNode();
        row1.add("alpha"); row1.add("beta");
        ArrayNode row2 = JsonNodeFactory.instance.arrayNode();
        row2.add("gamma"); row2.add("delta");
        data.add(row1);
        data.add(row2);

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertNotEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode(),
                        "Array of strings must not produce SPARKLINE");
        assertNotEquals(PrimitiveRenderMode.BAR, layout.getRenderMode(),
                        "Array of strings must not produce BAR");
    }

    // ---- Mixed scalar + array → TEXT (inconsistent types) ----

    @Test
    void mixedScalarAndArrayDataProducesTextMode() {
        var style = TestLayouts.mockPrimitiveStyle(7.0);
        var layout = new PrimitiveLayout(new Primitive("mixed"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // First element is a scalar number
        data.add(42.0);
        // Second element is an array of numbers
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        arr.add(1.0); arr.add(2.0);
        data.add(arr);

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        // Mixed types — neither purely scalar nor purely array — must not be BAR or SPARKLINE
        assertNotEquals(PrimitiveRenderMode.BAR, layout.getRenderMode(),
                        "Mixed scalar+array must not be BAR");
        assertNotEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode(),
                        "Mixed scalar+array must not be SPARKLINE");
    }

    // ---- Explicit render-mode=sparkline stylesheet override ----

    @Test
    void explicitRenderModeSparklineOverrideHonored() {
        var style = TestLayouts.mockPrimitiveStyle(7.0);
        var layout = new PrimitiveLayout(new Primitive("metric"), style);

        DefaultLayoutStylesheet sheet = new DefaultLayoutStylesheet(null);
        SchemaPath path = new SchemaPath("metric");
        sheet.setOverride(path, "render-mode", "sparkline");
        layout.buildPaths(path, null);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // Plain text data — would normally be TEXT, but override forces SPARKLINE
        data.add("some-value");
        data.add("other-value");

        Style model = mock(Style.class);
        when(model.getStylesheet()).thenReturn(sheet);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode(),
                     "Explicit render-mode=sparkline must force SPARKLINE regardless of data type");
    }

    // ---- Empty array → TEXT (no data to sparkline) ----

    @Test
    void emptyArrayProducesTextMode() {
        var style = TestLayouts.mockPrimitiveStyle(7.0);
        var layout = new PrimitiveLayout(new Primitive("empty"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        // A single row whose value is an empty array
        ArrayNode emptyArr = JsonNodeFactory.instance.arrayNode();
        data.add(emptyArr);

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertNotEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode(),
                        "Empty array must not produce SPARKLINE");
        assertNotEquals(PrimitiveRenderMode.BAR, layout.getRenderMode(),
                        "Empty array must not produce BAR");
    }

    // ---- SparklineStats populated with correct min/max/q1/q3/seriesLength ----

    @Test
    void sparklineStatsPopulatedCorrectly() {
        var style = TestLayouts.mockPrimitiveStyle(7.0);
        var layout = new PrimitiveLayout(new Primitive("values"), style);

        // Rows with known numeric arrays: [1,2,3] and [4,5,6]
        // All values flattened: [1,2,3,4,5,6]
        // min=1, max=6, seriesLength=6
        // sorted: [1,2,3,4,5,6] -> q1=index(1)=2, q3=index(4)=5
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ArrayNode row1 = JsonNodeFactory.instance.arrayNode();
        row1.add(1.0); row1.add(2.0); row1.add(3.0);
        ArrayNode row2 = JsonNodeFactory.instance.arrayNode();
        row2.add(4.0); row2.add(5.0); row2.add(6.0);
        data.add(row1);
        data.add(row2);

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode());

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result);
        SparklineStats ss = result.sparklineStats();
        assertNotNull(ss, "sparklineStats must be populated for SPARKLINE mode");

        assertEquals(1.0, ss.seriesMin(), 1e-9, "seriesMin must be 1.0");
        assertEquals(6.0, ss.seriesMax(), 1e-9, "seriesMax must be 6.0");
        assertEquals(6, ss.seriesLength(), "seriesLength must be 6 (all flattened values)");
        // q1 and q3 must be within [1.0, 6.0]
        assertTrue(ss.q1() >= ss.seriesMin() && ss.q1() <= ss.seriesMax(),
                   "q1 must be within [seriesMin, seriesMax]");
        assertTrue(ss.q3() >= ss.q1() && ss.q3() <= ss.seriesMax(),
                   "q3 must be >= q1 and <= seriesMax");
    }

    @Test
    void sparklineStatsSeriesLengthCountsAllFlattenedValues() {
        var style = TestLayouts.mockPrimitiveStyle(7.0);
        var layout = new PrimitiveLayout(new Primitive("ts"), style);

        // 3 rows × 4 values each = 12 total
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        for (int r = 0; r < 3; r++) {
            ArrayNode row = JsonNodeFactory.instance.arrayNode();
            for (int i = 0; i < 4; i++) {
                row.add((double) (r * 4 + i));
            }
            data.add(row);
        }

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        assertEquals(PrimitiveRenderMode.SPARKLINE, layout.getRenderMode());
        SparklineStats ss = layout.getMeasureResult().sparklineStats();
        assertNotNull(ss);
        assertEquals(12, ss.seriesLength(), "seriesLength must count all flattened values (3×4=12)");
    }
}

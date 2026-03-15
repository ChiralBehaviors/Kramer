// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for A3: PrimitiveLayout MeasureResult dual-write.
 * Verifies that measure() populates both mutable fields AND MeasureResult,
 * and that MeasureResult survives layout()/clear().
 */
class PrimitiveMeasureResultTest {

    @Test
    void measureResultMatchesMutableFields() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("name"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("Alice");
        data.add("Bob");
        data.add("Charlie");

        Style model = new Style();
        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result, "MeasureResult should be populated after measure()");

        assertEquals(layout.getLabelWidth(), result.labelWidth());
        assertEquals(layout.columnWidth, result.columnWidth());
        assertEquals(layout.dataWidth, result.dataWidth());
        assertEquals(layout.maxWidth, result.maxWidth());
        assertEquals(layout.averageCardinality, result.averageCardinality());
        assertEquals(layout.isVariableLength(), result.isVariableLength());
    }

    @Test
    void measureResultSurvivesLayoutClear() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("date"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("2026-01-01");
        data.add("2026-06-15");

        Style model = new Style();
        layout.measure(data, n -> n, model);

        MeasureResult before = layout.getMeasureResult();
        assertNotNull(before);

        // layout() calls clear() internally
        layout.layout(500);

        MeasureResult after = layout.getMeasureResult();
        assertSame(before, after,
                   "MeasureResult must survive layout()/clear()");
    }

    @Test
    void emptyDataProducesVariableLengthFallback() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("empty"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode(); // empty

        Style model = new Style();
        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result);
        assertTrue(result.isVariableLength(),
                   "Empty data should default to variable-length (safe fallback)");
    }

    @Test
    void getMeasureResultNullBeforeMeasure() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("x"), style);

        assertNull(layout.getMeasureResult(),
                   "MeasureResult should be null before measure() is called");
    }
}

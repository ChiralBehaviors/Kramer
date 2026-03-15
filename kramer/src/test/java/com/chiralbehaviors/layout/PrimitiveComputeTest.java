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
 * Tests for B1: PrimitiveLayout compute* result-returning methods.
 */
class PrimitiveComputeTest {

    @Test
    void computeCompressMatchesMutableCompress() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("date"), style);

        // Fixed-length data
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("2026-01-01");
        data.add("2026-06-15");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);
        layout.layout(500);

        // Mutable path
        layout.compress(500);
        double mutableJustified = layout.getJustifiedWidth();

        // Reset and use compute path
        layout.layout(500); // re-clear
        CompressResult result = layout.computeCompress(500);

        assertEquals(mutableJustified, result.justifiedWidth(),
                     "computeCompress should produce same justifiedWidth as compress");
    }

    @Test
    void computeCellHeightMatchesMutable() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("name"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("Alice");
        data.add("Bob");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);
        layout.layout(500);
        layout.compress(500);

        // Mutable path
        double mutableHeight = layout.cellHeight(1, 500);

        // Compute path
        HeightResult result = layout.computeCellHeight(1, 500);

        assertEquals(mutableHeight, result.height(),
                     "computeCellHeight should produce same height");
        assertEquals(layout.getCellHeight(), result.cellHeight(),
                     "computeCellHeight should produce same cellHeight");
    }

    @Test
    void computeLayoutReturnsResult() {
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("x"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("hello");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        LayoutResult result = layout.computeLayout(500);
        assertNotNull(result);
        // PrimitiveLayout doesn't use table mode
        assertFalse(result.useTable());
    }
}

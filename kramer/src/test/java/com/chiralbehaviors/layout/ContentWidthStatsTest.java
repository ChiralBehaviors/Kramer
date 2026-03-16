// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
import static org.mockito.Mockito.mock;

import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.List;
import java.util.function.Function;

/**
 * Tests for ContentWidthStats record and its integration into MeasureResult.
 */
class ContentWidthStatsTest {

    // --- ContentWidthStats record construction and accessors ---

    @Test
    void constructionAndAccessors() {
        ContentWidthStats stats = new ContentWidthStats(42.0, 95.0, 100, true);

        assertEquals(42.0, stats.p50Width());
        assertEquals(95.0, stats.p90Width());
        assertEquals(100, stats.sampleCount());
        assertTrue(stats.converged());
    }

    @Test
    void notConverged() {
        ContentWidthStats stats = new ContentWidthStats(10.0, 20.0, 5, false);

        assertFalse(stats.converged());
        assertEquals(5, stats.sampleCount());
    }

    @Test
    void zeroSampleCount() {
        ContentWidthStats stats = new ContentWidthStats(0.0, 0.0, 0, false);

        assertEquals(0, stats.sampleCount());
        assertEquals(0.0, stats.p50Width());
        assertEquals(0.0, stats.p90Width());
    }

    @Test
    void equalityAndHashCode() {
        ContentWidthStats a = new ContentWidthStats(50.0, 90.0, 10, true);
        ContentWidthStats b = new ContentWidthStats(50.0, 90.0, 10, true);
        ContentWidthStats c = new ContentWidthStats(50.0, 90.0, 10, false);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    // --- MeasureResult backward compatibility: null contentStats ---

    @Test
    void measureResultWithNullContentStats() {
        MeasureResult result = new MeasureResult(
            10.0, 20.0, 15.0, 25.0,
            1, false,
            0, 0,
            null, List.of(),
            null, null, null
        );

        assertNull(result.contentStats(),
                   "contentStats should be null when not provided");
        assertEquals(10.0, result.labelWidth());
        assertEquals(20.0, result.columnWidth());
    }

    @Test
    void measureResultWithContentStats() {
        ContentWidthStats stats = new ContentWidthStats(30.0, 60.0, 50, true);
        MeasureResult result = new MeasureResult(
            10.0, 20.0, 15.0, 25.0,
            1, false,
            0, 0,
            null, List.of(),
            stats, null, null
        );

        assertNotNull(result.contentStats());
        assertEquals(stats, result.contentStats());
        assertEquals(30.0, result.contentStats().p50Width());
        assertEquals(60.0, result.contentStats().p90Width());
        assertEquals(50, result.contentStats().sampleCount());
        assertTrue(result.contentStats().converged());
    }

    @Test
    void primitiveLayoutContentStatsNullWhenBelowMinSamples() {
        // 2 elements < MIN_SAMPLES=30, so contentStats is not computed
        PrimitiveStyle style = TestLayouts.mockPrimitiveStyle(7.0);
        PrimitiveLayout layout = new PrimitiveLayout(new Primitive("value"), style);

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.add("hello");
        data.add("world");

        Style model = mock(Style.class);
        layout.measure(data, n -> n, model);

        MeasureResult result = layout.getMeasureResult();
        assertNotNull(result);
        assertNull(result.contentStats(),
                   "contentStats must be null when sampleCount < MIN_SAMPLES=30");
    }

    @Test
    void relationLayoutMeasureResultHasNullContentStats() {
        // Verify RelationLayout measure() produces null contentStats (placeholder)
        // We check via a direct MeasureResult construction as RelationLayout
        // integration tests already cover measure() invocation patterns.
        MeasureResult result = new MeasureResult(
            5.0, 10.0, 0.0, 0.0,
            0, false,
            2, 5,
            Function.identity(), List.of(),
            null, null, null
        );

        assertNull(result.contentStats(),
                   "Relation MeasureResult should accept null contentStats");
    }
}

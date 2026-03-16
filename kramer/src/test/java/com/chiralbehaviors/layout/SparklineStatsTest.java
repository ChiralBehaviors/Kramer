// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for Kramer-woy: SparklineStats record and MeasureResult integration.
 */
class SparklineStatsTest {

    // --- SparklineStats record construction and accessors ---

    @Test
    void constructionAndAccessors() {
        SparklineStats stats = new SparklineStats(1.0, 99.0, 25.0, 75.0, 50);

        assertEquals(1.0, stats.seriesMin(), 1e-9);
        assertEquals(99.0, stats.seriesMax(), 1e-9);
        assertEquals(25.0, stats.q1(), 1e-9);
        assertEquals(75.0, stats.q3(), 1e-9);
        assertEquals(50, stats.seriesLength());
    }

    @Test
    void equalityAndHashCode() {
        SparklineStats a = new SparklineStats(0.0, 100.0, 20.0, 80.0, 10);
        SparklineStats b = new SparklineStats(0.0, 100.0, 20.0, 80.0, 10);
        SparklineStats c = new SparklineStats(0.0, 100.0, 20.0, 80.0, 11);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void zeroSeriesLength() {
        SparklineStats stats = new SparklineStats(0.0, 0.0, 0.0, 0.0, 0);
        assertEquals(0, stats.seriesLength());
    }

    @Test
    void negativeRangeAllowed() {
        SparklineStats stats = new SparklineStats(-50.0, -10.0, -40.0, -20.0, 5);
        assertEquals(-50.0, stats.seriesMin(), 1e-9);
        assertEquals(-10.0, stats.seriesMax(), 1e-9);
        assertEquals(-40.0, stats.q1(), 1e-9);
        assertEquals(-20.0, stats.q3(), 1e-9);
    }

    // --- MeasureResult with non-null sparklineStats ---

    @Test
    void measureResultWithNonNullSparklineStats() {
        SparklineStats ss = new SparklineStats(2.0, 98.0, 30.0, 70.0, 20);
        MeasureResult result = new MeasureResult(
            10.0, 20.0, 15.0, 25.0,
            1, false,
            0, 0,
            null, List.of(),
            null, null, null, ss
        );

        assertNotNull(result.sparklineStats());
        assertEquals(2.0, result.sparklineStats().seriesMin(), 1e-9);
        assertEquals(98.0, result.sparklineStats().seriesMax(), 1e-9);
        assertEquals(30.0, result.sparklineStats().q1(), 1e-9);
        assertEquals(70.0, result.sparklineStats().q3(), 1e-9);
        assertEquals(20, result.sparklineStats().seriesLength());
    }

    // --- MeasureResult with null sparklineStats (backward compatibility) ---

    @Test
    void measureResultWithNullSparklineStats() {
        MeasureResult result = new MeasureResult(
            10.0, 20.0, 15.0, 25.0,
            1, false,
            0, 0,
            null, List.of(),
            null, null, null, null
        );

        assertNull(result.sparklineStats(),
                   "sparklineStats should be null when not provided");
    }

    @Test
    void existingThirteenParamSitesStillCompileWithNull() {
        // Simulate the pattern used at all existing call sites after migration:
        // pass null as the 14th argument
        MeasureResult result = new MeasureResult(
            5.0, 10.0, 8.0, 12.0,
            2, true,
            1, 3,
            n -> n, List.of(),
            new ContentWidthStats(5.0, 9.0, 30, true),
            new NumericStats(0.0, 10.0),
            null,
            null   // sparklineStats = null
        );

        assertNull(result.sparklineStats());
        assertNotNull(result.contentStats());
        assertNotNull(result.numericStats());
    }
}

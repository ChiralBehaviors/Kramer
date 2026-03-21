// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for ColumnPartitioner — DP-optimal column partitioning (Bakke F2).
 * Painter's partition problem: minimize max column height.
 */
class ColumnPartitionerTest {

    // -----------------------------------------------------------------------
    // DP optimal: basic cases
    // -----------------------------------------------------------------------

    @Test
    void singleColumnReturnsAllFields() {
        double[] heights = {10, 20, 30, 40};
        int[] sizes = ColumnPartitioner.dpOptimal().partition(heights, 1);
        assertArrayEquals(new int[]{4}, sizes,
            "Single column should contain all fields");
    }

    @Test
    void equalHeightsEvenSplit() {
        // 4 fields of equal height into 2 columns → 2 each
        double[] heights = {20, 20, 20, 20};
        int[] sizes = ColumnPartitioner.dpOptimal().partition(heights, 2);
        assertArrayEquals(new int[]{2, 2}, sizes,
            "Equal heights should split evenly");
    }

    @Test
    void unequalHeightsOptimalSplit() {
        // heights: [10, 10, 10, 30] into 2 columns
        // Greedy L→R: [10,10,10] vs [30] → max=30
        // Optimal: [10,10] vs [10,30] → max=40? No — [10,10,10] vs [30] → max=30
        // Actually [10,10,10]=30 vs [30]=30 → max=30, both partitions give 30
        // Better example: [40, 10, 10, 10] into 2 columns
        // [40] vs [10,10,10]=30 → max=40
        // [40,10]=50 vs [10,10]=20 → max=50
        // Optimal is [40] vs [10,10,10] → max=40
        double[] heights = {40, 10, 10, 10};
        int[] sizes = ColumnPartitioner.dpOptimal().partition(heights, 2);
        assertEquals(2, sizes.length);
        // First column should have just 1 field (the tall one)
        assertEquals(1, sizes[0], "Tall field alone in column 0");
        assertEquals(3, sizes[1], "Short fields in column 1");
    }

    @Test
    void threeColumnsOptimalPartition() {
        // heights: [30, 10, 10, 20, 10, 20] into 3 columns
        // Total = 100, ideal = 33.3 per column
        // Optimal: [30] [10,10,20] [10,20] → max(30, 40, 30) = 40
        // Or: [30,10] [10,20] [10,20] → max(40, 30, 30) = 40
        // Or: [30] [10,10] [20,10,20] → max(30, 20, 50) = 50
        // Best: [30,10] [10,20] [10,20] or [30] [10,10,20] [10,20] both 40
        double[] heights = {30, 10, 10, 20, 10, 20};
        int[] sizes = ColumnPartitioner.dpOptimal().partition(heights, 3);
        assertEquals(3, sizes.length);
        assertEquals(6, sizes[0] + sizes[1] + sizes[2],
            "All fields accounted for");

        // Verify the max column height is optimal (40)
        double maxHeight = maxColumnHeight(heights, sizes);
        assertEquals(40.0, maxHeight, 0.01,
            "DP should find optimal max height of 40");
    }

    @Test
    void moreColumnsThanFields() {
        // 2 fields into 4 columns → first 2 columns get 1 each, rest empty
        double[] heights = {10, 20};
        int[] sizes = ColumnPartitioner.dpOptimal().partition(heights, 4);
        assertEquals(4, sizes.length);
        assertEquals(2, sizes[0] + sizes[1] + sizes[2] + sizes[3],
            "All fields accounted for");
        // Each non-empty column should have exactly 1 field
        int nonEmpty = 0;
        for (int s : sizes) if (s > 0) nonEmpty++;
        assertEquals(2, nonEmpty, "Only 2 columns should have fields");
    }

    @Test
    void singleFieldSingleColumn() {
        double[] heights = {50};
        int[] sizes = ColumnPartitioner.dpOptimal().partition(heights, 1);
        assertArrayEquals(new int[]{1}, sizes);
    }

    @Test
    void singleFieldMultipleColumns() {
        double[] heights = {50};
        int[] sizes = ColumnPartitioner.dpOptimal().partition(heights, 3);
        assertEquals(3, sizes.length);
        assertEquals(1, sizes[0] + sizes[1] + sizes[2]);
    }

    // -----------------------------------------------------------------------
    // DP beats greedy on known pathological case
    // -----------------------------------------------------------------------

    @Test
    void dpBeatsGreedyOnPathologicalCase() {
        // Pathological for greedy: [5, 5, 5, 5, 40, 5, 5, 5, 5] into 3 cols
        // Greedy slides from left → may produce suboptimal partition
        // Optimal: [5,5,5,5] [40] [5,5,5,5] → max(20, 40, 20) = 40
        double[] heights = {5, 5, 5, 5, 40, 5, 5, 5, 5};
        int[] dpSizes = ColumnPartitioner.dpOptimal().partition(heights, 3);
        int[] greedySizes = ColumnPartitioner.greedy().partition(heights, 3);

        double dpMax = maxColumnHeight(heights, dpSizes);
        double greedyMax = maxColumnHeight(heights, greedySizes);

        assertTrue(dpMax <= greedyMax,
            String.format("DP (%.1f) should be <= greedy (%.1f)", dpMax, greedyMax));
    }

    // -----------------------------------------------------------------------
    // Greedy: basic validation
    // -----------------------------------------------------------------------

    @Test
    void greedyPartitionsAllFields() {
        double[] heights = {10, 20, 30, 40};
        int[] sizes = ColumnPartitioner.greedy().partition(heights, 2);
        assertEquals(2, sizes.length);
        assertEquals(4, sizes[0] + sizes[1], "All fields accounted for");
    }

    @Test
    void greedySingleColumn() {
        double[] heights = {10, 20, 30};
        int[] sizes = ColumnPartitioner.greedy().partition(heights, 1);
        assertArrayEquals(new int[]{3}, sizes);
    }

    // -----------------------------------------------------------------------
    // Contract: partition sizes sum to field count
    // -----------------------------------------------------------------------

    @Test
    void partitionSizesAlwaysSumToFieldCount() {
        double[] heights = {15, 25, 35, 10, 20, 30, 5};
        for (int k = 1; k <= 5; k++) {
            int[] dpSizes = ColumnPartitioner.dpOptimal().partition(heights, k);
            int sum = 0;
            for (int s : dpSizes) sum += s;
            assertEquals(heights.length, sum,
                "DP partition sizes must sum to field count for k=" + k);

            int[] greedySizes = ColumnPartitioner.greedy().partition(heights, k);
            sum = 0;
            for (int s : greedySizes) sum += s;
            assertEquals(heights.length, sum,
                "Greedy partition sizes must sum to field count for k=" + k);
        }
    }

    // -----------------------------------------------------------------------
    // DP is always <= greedy (or equal) on random-ish inputs
    // -----------------------------------------------------------------------

    @Test
    void dpNeverWorseThanGreedy() {
        // Several test inputs
        double[][] inputs = {
            {40, 10, 10, 10},
            {10, 10, 10, 40},
            {30, 10, 10, 20, 10, 20},
            {100, 5, 5, 5, 5, 5, 5},
            {20, 20, 20, 20, 20},
        };
        for (double[] heights : inputs) {
            for (int k = 2; k <= Math.min(4, heights.length); k++) {
                double dpMax = maxColumnHeight(heights,
                    ColumnPartitioner.dpOptimal().partition(heights, k));
                double greedyMax = maxColumnHeight(heights,
                    ColumnPartitioner.greedy().partition(heights, k));
                assertTrue(dpMax <= greedyMax + 0.01,
                    String.format("DP should be <= greedy for k=%d: dp=%.1f, greedy=%.1f",
                        k, dpMax, greedyMax));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static double maxColumnHeight(double[] heights, int[] sizes) {
        double max = 0;
        int idx = 0;
        for (int size : sizes) {
            double colHeight = 0;
            for (int j = 0; j < size; j++) {
                if (idx < heights.length) {
                    colHeight += heights[idx++];
                }
            }
            max = Math.max(max, colHeight);
        }
        return max;
    }
}

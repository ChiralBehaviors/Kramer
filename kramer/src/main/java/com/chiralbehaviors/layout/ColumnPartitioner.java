// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

/**
 * Strategy for partitioning N ordered fields into K contiguous columns,
 * minimizing the maximum column height (sum of field heights in each column).
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@link #dpOptimal()} — globally optimal via dynamic programming
 *       (painter's partition problem, O(N²K) time)</li>
 *   <li>{@link #greedy()} — left-to-right greedy that approximates balance
 *       by filling columns up to the ideal average height</li>
 * </ul>
 *
 * @see ColumnSet#compress
 */
@FunctionalInterface
public interface ColumnPartitioner {

    /**
     * Partition {@code fieldHeights.length} fields into {@code numColumns}
     * contiguous groups.
     *
     * @param fieldHeights height of each field (in layout order)
     * @param numColumns   number of columns (K ≥ 1)
     * @return array of length {@code numColumns} where element {@code k} is the
     *         number of fields assigned to column {@code k}; values sum to
     *         {@code fieldHeights.length}
     */
    int[] partition(double[] fieldHeights, int numColumns);

    /**
     * DP-optimal partitioner. Solves the painter's partition problem exactly.
     * Minimizes the maximum column height across all possible contiguous
     * partitions.
     */
    static ColumnPartitioner dpOptimal() {
        return (heights, k) -> {
            int n = heights.length;
            if (n == 0) {
                int[] sizes = new int[k];
                return sizes;
            }
            if (k >= n) {
                // More columns than fields — one field per column
                int[] sizes = new int[k];
                for (int i = 0; i < n; i++) {
                    sizes[i] = 1;
                }
                return sizes;
            }
            if (k == 1) {
                return new int[]{n};
            }

            // Prefix sums for O(1) range height queries
            double[] prefix = new double[n + 1];
            for (int i = 0; i < n; i++) {
                prefix[i + 1] = prefix[i] + heights[i];
            }

            // dp[i][j] = min possible max-height partitioning first i fields
            //             into j columns
            // split[i][j] = optimal split point for backtracking
            double[][] dp = new double[n + 1][k + 1];
            int[][] split = new int[n + 1][k + 1];

            // Base: all fields in 1 column
            for (int i = 0; i <= n; i++) {
                dp[i][1] = prefix[i];
            }
            // Initialize rest to infinity
            for (int i = 0; i <= n; i++) {
                for (int j = 2; j <= k; j++) {
                    dp[i][j] = Double.MAX_VALUE;
                }
            }

            // Fill DP table
            for (int j = 2; j <= k; j++) {
                for (int i = j; i <= n; i++) {
                    // Try all split points: columns 1..j-1 get first s fields,
                    // column j gets fields s+1..i
                    for (int s = j - 1; s < i; s++) {
                        double cost = Math.max(dp[s][j - 1],
                                               prefix[i] - prefix[s]);
                        if (cost < dp[i][j]) {
                            dp[i][j] = cost;
                            split[i][j] = s;
                        }
                    }
                }
            }

            // Backtrack to recover partition boundaries
            int[] boundaries = new int[k + 1];
            boundaries[k] = n;
            for (int j = k; j >= 2; j--) {
                boundaries[j - 1] = split[boundaries[j]][j];
            }
            boundaries[0] = 0;

            // Convert boundaries to sizes
            int[] sizes = new int[k];
            for (int j = 0; j < k; j++) {
                sizes[j] = boundaries[j + 1] - boundaries[j];
            }
            return sizes;
        };
    }

    /**
     * Greedy partitioner. Fills columns left-to-right, starting a new column
     * when the current column exceeds the ideal average height.
     * Fast but can produce suboptimal results when field heights vary widely.
     */
    static ColumnPartitioner greedy() {
        return (heights, k) -> {
            int n = heights.length;
            if (n == 0) {
                return new int[k];
            }
            if (k >= n) {
                int[] sizes = new int[k];
                for (int i = 0; i < n; i++) {
                    sizes[i] = 1;
                }
                return sizes;
            }
            if (k == 1) {
                return new int[]{n};
            }

            double total = 0;
            for (double h : heights) total += h;
            double ideal = total / k;

            int[] sizes = new int[k];
            int col = 0;
            double colHeight = 0;
            int fieldsRemaining = n;

            for (int i = 0; i < n; i++) {
                int colsRemaining = k - col;
                // Ensure remaining columns get at least one field each
                if (fieldsRemaining <= colsRemaining) {
                    sizes[col]++;
                    col++;
                    colHeight = 0;
                    fieldsRemaining--;
                    continue;
                }

                sizes[col]++;
                colHeight += heights[i];
                fieldsRemaining--;

                // Move to next column if we've exceeded ideal and there are
                // more columns available
                if (colHeight >= ideal && col < k - 1) {
                    col++;
                    colHeight = 0;
                }
            }

            return sizes;
        };
    }
}

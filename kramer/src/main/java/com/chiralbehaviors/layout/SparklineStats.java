// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

/**
 * Statistical summary for sparkline rendering of a primitive series.
 * Captured during the measure phase; used by sparkline renderers at layout time.
 *
 * <p>No {@code lastValue} field is included here — that is per-cell state
 * supplied in {@code updateItem} rather than a measure-phase aggregate.
 *
 * @param seriesMin    minimum value in the series
 * @param seriesMax    maximum value in the series
 * @param q1           first quartile (25th percentile)
 * @param q3           third quartile (75th percentile)
 * @param seriesLength number of data points in the series
 */
public record SparklineStats(double seriesMin, double seriesMax, double q1, double q3, int seriesLength) {
}

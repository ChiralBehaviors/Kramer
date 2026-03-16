// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

/**
 * Statistical summary of numeric content widths — or, more precisely, the
 * actual numeric values observed during the measure phase when a Primitive
 * field is found to be entirely numeric.
 *
 * <p>This record is set on {@link MeasureResult} only when every sampled value
 * can be parsed as a number. When present it signals that the field should be
 * rendered in {@link PrimitiveRenderMode#BAR} mode.
 *
 * @param numericMin  smallest numeric value observed across all sampled rows
 * @param numericMax  largest  numeric value observed across all sampled rows
 */
public record NumericStats(double numericMin, double numericMax) {
}

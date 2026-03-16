// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

/**
 * Statistical summary of observed content widths from the measure phase.
 * Captures percentile data used by the adaptive layout algorithm to choose
 * between outline and table rendering modes.
 *
 * @param p50Width     median (50th percentile) content width in pixels
 * @param p90Width     90th percentile content width in pixels
 * @param sampleCount  number of data values sampled
 * @param converged    true when the sample set is stable enough to trust
 */
public record ContentWidthStats(double p50Width, double p90Width, int sampleCount, boolean converged) {
}

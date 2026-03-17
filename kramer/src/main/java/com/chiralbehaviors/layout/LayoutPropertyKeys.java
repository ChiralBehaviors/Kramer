// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

/**
 * CSS / schema property key constants used by the Kramer layout engine.
 *
 * <p>Deferred consolidation: the following additional property keys exist in
 * the codebase but have not been centralised here yet:
 * {@code stat-min-samples}, {@code stat-convergence-epsilon},
 * {@code stat-convergence-k}, {@code badge-cardinality-threshold},
 * {@code pivot-field}.
 */
public final class LayoutPropertyKeys {

    public static final String VISIBLE      = "visible";
    public static final String RENDER_MODE  = "render-mode";
    public static final String HIDE_IF_EMPTY = "hide-if-empty";
    public static final String SORT_FIELDS  = "sort-fields";

    // Expression language properties (RDR-021)
    /** Per-row boolean predicate; false/null rows excluded from layout. */
    public static final String FILTER_EXPRESSION    = "filter-expression";
    /** Per-row computation; result replaces field value for the target Primitive. */
    public static final String FORMULA_EXPRESSION   = "formula-expression";
    /** Cross-row reduction (sum, count, avg, min, max); rendering deferred. */
    public static final String AGGREGATE_EXPRESSION = "aggregate-expression";
    /**
     * Per-row sort key derivation. Takes precedence over {@link #SORT_FIELDS};
     * sort-fields is used as tiebreaker when both are present.
     */
    public static final String SORT_EXPRESSION      = "sort-expression";

    // Sparkline rendering properties
    public static final String SPARKLINE_BAND_VISIBLE    = "sparkline-band-visible";
    public static final String SPARKLINE_END_MARKER      = "sparkline-end-marker";
    public static final String SPARKLINE_MIN_MAX_MARKERS = "sparkline-min-max-markers";
    public static final String SPARKLINE_LINE_WIDTH      = "sparkline-line-width";
    public static final String SPARKLINE_BAND_OPACITY    = "sparkline-band-opacity";
    public static final String SPARKLINE_MIN_WIDTH       = "sparkline-min-width";

    private LayoutPropertyKeys() {}
}

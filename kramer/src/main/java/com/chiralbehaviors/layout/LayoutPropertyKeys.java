// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

/**
 * CSS / schema property key constants used by the Kramer layout engine.
 *
 * <p>Deferred consolidation: the following additional property keys exist in
 * the codebase but have not been centralised here yet:
 * {@code stat-min-samples}, {@code stat-convergence-epsilon},
 * {@code stat-convergence-k}, {@code badge-cardinality-threshold}.
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

    // Crosstab / pivot properties
    /** Field name whose distinct values become pivot columns in crosstab mode. */
    public static final String PIVOT_FIELD = "pivot-field";

    // Frozen / aggregate position properties
    /** Prevents user mutation of this field's query state via InteractionHandler. */
    public static final String FROZEN             = "frozen";
    /** Where aggregate results render: "footer" or null (hidden). */
    public static final String AGGREGATE_POSITION = "aggregate-position";
    /** Printf-style format string for cell display (e.g., "%.2f", "%d"). */
    public static final String CELL_FORMAT        = "cell-format";
    /** User-set column width override in pixels; null = use computed justifiedWidth. */
    public static final String COLUMN_WIDTH       = "column-width";
    /** Hide consecutive duplicate rows in a Relation (SIEUFERD COLLAPSEDUPLICATEROWS). */
    public static final String COLLAPSE_DUPLICATES = "collapse-duplicates";

    // Sparkline rendering properties
    public static final String SPARKLINE_BAND_VISIBLE    = "sparkline-band-visible";
    public static final String SPARKLINE_END_MARKER      = "sparkline-end-marker";
    public static final String SPARKLINE_MIN_MAX_MARKERS = "sparkline-min-max-markers";
    public static final String SPARKLINE_LINE_WIDTH      = "sparkline-line-width";
    public static final String SPARKLINE_BAND_OPACITY    = "sparkline-band-opacity";
    public static final String SPARKLINE_MIN_WIDTH       = "sparkline-min-width";

    private LayoutPropertyKeys() {}
}

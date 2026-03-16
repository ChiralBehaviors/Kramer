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

    private LayoutPropertyKeys() {}
}

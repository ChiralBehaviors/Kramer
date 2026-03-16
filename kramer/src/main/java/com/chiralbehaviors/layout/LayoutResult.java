// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

/**
 * Immutable result of the layout phase. Captures the table-vs-outline decision
 * and width-dependent layout geometry. Produced by layout(), recomputed per
 * resize.
 *
 * @see SchemaNodeLayout#layout(double)
 */
public record LayoutResult(
    RelationRenderMode relationMode,
    PrimitiveRenderMode primitiveMode,
    boolean useVerticalHeader,
    double tableColumnWidth,
    double columnHeaderIndentation,
    double constrainedColumnWidth,
    List<LayoutResult> childResults
) {
    public LayoutResult {
        childResults = childResults == null ? List.of() : List.copyOf(childResults);
    }

    /** Backward-compatible accessor: returns true when relationMode is TABLE. */
    public boolean useTable() {
        return relationMode == RelationRenderMode.TABLE;
    }
}

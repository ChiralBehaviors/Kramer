// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

/**
 * Unified decision tree node that captures all four layout phase results for a
 * single schema node. Combines measure, layout, compress, and height results
 * alongside the column-set snapshots and child sub-trees produced during layout.
 *
 * <p>{@code fieldName} mirrors {@code path.leaf()} and is retained as a
 * convenience so renderers can call {@code data.get(fieldName)} without
 * re-deriving the leaf segment on every access.
 */
public record LayoutDecisionNode(
    SchemaPath path,
    String fieldName,
    MeasureResult measureResult,
    LayoutResult layoutResult,
    CompressResult compressResult,
    HeightResult heightResult,
    List<ColumnSetSnapshot> columnSetSnapshots,
    List<LayoutDecisionNode> childNodes
) {
    public LayoutDecisionNode {
        childNodes = childNodes == null ? List.of() : List.copyOf(childNodes);
        columnSetSnapshots = columnSetSnapshots == null ? List.of() : List.copyOf(columnSetSnapshots);
    }
}

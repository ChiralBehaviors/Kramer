// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

/**
 * Immutable result of the compress phase. Captures justified geometry and
 * column set assignments for outline mode. In outline mode, cellHeight is set
 * here; in table mode it is set in HeightResult instead.
 *
 * @see SchemaNodeLayout#compress(double)
 */
public record CompressResult(
    double justifiedWidth,
    List<ColumnSet> columnSets,
    double cellHeight,
    List<CompressResult> childResults
) {
    public CompressResult {
        columnSets = columnSets == null ? List.of() : List.copyOf(columnSets);
        childResults = childResults == null ? List.of() : List.copyOf(childResults);
    }
}

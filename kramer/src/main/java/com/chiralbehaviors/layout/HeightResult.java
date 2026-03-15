// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

/**
 * Immutable result of the height computation phase. Captures final sizing
 * after cellHeight/calculateRootHeight. In table mode, cellHeight is set here
 * (via calculateTableHeight); in outline mode it was already set in
 * CompressResult.
 *
 * @see SchemaNodeLayout#cellHeight(int, double)
 */
public record HeightResult(
    double height,
    double cellHeight,
    int resolvedCardinality,
    double columnHeaderHeight,
    List<HeightResult> childResults
) {
    public HeightResult {
        childResults = childResults == null ? List.of() : List.copyOf(childResults);
    }
}

// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

public record CompressResult(
    double justifiedWidth,
    List<ColumnSetSnapshot> columnSetSnapshots,
    double cellHeight,
    List<CompressResult> childResults
) {
    public CompressResult {
        columnSetSnapshots = columnSetSnapshots == null ? List.of() : List.copyOf(columnSetSnapshots);
        childResults = childResults == null ? List.of() : List.copyOf(childResults);
    }
}

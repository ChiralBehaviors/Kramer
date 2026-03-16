// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;
import java.util.Objects;

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
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        assert fieldName.equals(path.leaf()) : "fieldName must match path.leaf(); got fieldName='" + fieldName + "', path.leaf()='" + path.leaf() + "'";
        // measureResult/layoutResult/compressResult/heightResult may be partially populated (nullable)
        childNodes = childNodes == null ? List.of() : List.copyOf(childNodes);
        columnSetSnapshots = columnSetSnapshots == null ? List.of() : List.copyOf(columnSetSnapshots);
    }
}

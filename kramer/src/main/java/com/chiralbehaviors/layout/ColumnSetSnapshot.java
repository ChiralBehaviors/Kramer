// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

/**
 * Immutable snapshot of a ColumnSet's layout decision: the ordered columns
 * and the resolved cell height.
 */
public record ColumnSetSnapshot(List<ColumnSnapshot> columns, double height) {
    public ColumnSetSnapshot {
        columns = List.copyOf(columns);
    }
}

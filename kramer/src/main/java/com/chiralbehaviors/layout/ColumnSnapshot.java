// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

/**
 * Immutable snapshot of a single column's state: its width and the ordered
 * names of the fields it contains.
 */
public record ColumnSnapshot(double width, List<String> fieldNames) {
    public ColumnSnapshot {
        fieldNames = List.copyOf(fieldNames);
    }
}

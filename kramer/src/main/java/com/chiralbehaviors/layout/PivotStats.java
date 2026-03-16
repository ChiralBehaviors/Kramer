// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

/**
 * Distinct pivot values collected during the measure phase for a Relation field
 * configured with a {@code pivot-field} stylesheet property.
 *
 * <p>When a relation's stylesheet specifies {@code pivot-field}, RelationLayout
 * scans the sorted+filtered datum array after sort/filter and collects the set
 * of distinct string values for that field. The result is stored here for use
 * by downstream layout and rendering phases.
 *
 * @param pivotValues  immutable list of distinct pivot-field values (insertion-ordered)
 * @param pivotCount   number of distinct pivot values; equals {@code pivotValues.size()}
 */
public record PivotStats(List<String> pivotValues, int pivotCount) {
    public PivotStats {
        pivotValues = List.copyOf(pivotValues);
        assert pivotCount == pivotValues.size() : "pivotCount mismatch";
    }
}

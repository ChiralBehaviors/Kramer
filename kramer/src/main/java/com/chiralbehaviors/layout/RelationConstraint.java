// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.util.List;

/**
 * Captures the sizing constraints for a single Relation node, used by
 * {@link ConstraintSolver} to determine the optimal render mode before layout().
 *
 * <p>Built from post-measure state: {@code tableWidth} comes from
 * {@link RelationLayout#calculateTableColumnWidth()}, {@code nestedHorizontalInset}
 * from {@link com.chiralbehaviors.layout.style.RelationStyle#getNestedHorizontalInset()},
 * and {@code availableWidth} from the width passed to {@code layout()}.
 *
 * @param path                  addressing key for this node in the schema tree
 * @param tableWidth            result of calculateTableColumnWidth()
 * @param nestedHorizontalInset horizontal inset that nestTableColumn() adds
 * @param availableWidth        width available to this node at layout time
 * @param children              constraints for direct Relation children
 * @param hardCrosstab          true when useCrosstab is explicitly set; fixed in solver
 */
public record RelationConstraint(
        SchemaPath path,
        double tableWidth,
        double nestedHorizontalInset,
        double availableWidth,
        List<RelationConstraint> children,
        boolean hardCrosstab
) {
    public RelationConstraint {
        children = children == null ? List.of() : List.copyOf(children);
    }

    /**
     * Returns true when this node's table rendering fits within its available width.
     * Mirrors the condition in RelationLayout.layout():
     * {@code tableWidth + nestedHorizontalInset <= availableWidth}.
     */
    public boolean fitsTable() {
        return tableWidth + nestedHorizontalInset <= availableWidth;
    }
}

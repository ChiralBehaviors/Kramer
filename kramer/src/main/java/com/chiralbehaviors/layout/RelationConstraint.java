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
 * {@code availableWidthAsOutline} from the outline-mode width passed to {@code layout()},
 * and {@code availableWidthAsTable} from the parent's {@code tableWidth / numChildren}
 * approximation (used when the parent is rendered in TABLE mode).
 *
 * @param path                    addressing key for this node in the schema tree
 * @param tableWidth              result of calculateTableColumnWidth()
 * @param nestedHorizontalInset   horizontal inset that nestTableColumn() adds
 * @param availableWidthAsOutline width available when parent renders as OUTLINE
 * @param availableWidthAsTable   approximate width available when parent renders as TABLE
 *                                (parent.tableWidth / numRelationChildren); use
 *                                {@code Double.MAX_VALUE} if parent width is unknown
 * @param children                constraints for direct Relation children
 * @param hardCrosstab            true when useCrosstab is explicitly set; fixed in solver
 */
public record RelationConstraint(
        SchemaPath path,
        double tableWidth,
        double nestedHorizontalInset,
        double availableWidthAsOutline,
        double availableWidthAsTable,
        List<RelationConstraint> children,
        boolean hardCrosstab
) {
    public RelationConstraint {
        children = children == null ? List.of() : List.copyOf(children);
    }

    /**
     * Returns true when this node's table rendering fits within its outline-mode
     * available width. Used when the parent is rendering as OUTLINE.
     * Mirrors the condition in RelationLayout.layout():
     * {@code tableWidth + nestedHorizontalInset <= availableWidthAsOutline}.
     */
    public boolean fitsTable() {
        return tableWidth + nestedHorizontalInset <= availableWidthAsOutline;
    }

    /**
     * Returns true when this node's table rendering fits within the table-mode
     * available width. Used when the parent is rendering as TABLE.
     */
    public boolean fitsTableInParentTable() {
        return tableWidth + nestedHorizontalInset <= availableWidthAsTable;
    }
}

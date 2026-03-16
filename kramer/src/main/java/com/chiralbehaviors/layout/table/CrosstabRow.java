// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.table;

import java.util.ArrayList;
import java.util.List;

import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * A single data row in a crosstab layout.
 *
 * <p>Each row contains:
 * <ol>
 *   <li>a row-header region (width = rowHeaderWidth) showing non-pivot field values</li>
 *   <li>one {@link CrosstabCell} per pivot value (each with width = pivotColumnWidth)</li>
 * </ol>
 *
 * <p>Rows are placed inside a VirtualFlow so that large datasets scroll
 * without allocating a JavaFX node per item.
 */
public class CrosstabRow extends Region {

    private static final String STYLE_CLASS = "crosstab-row";

    private final List<CrosstabCell> dataCells = new ArrayList<>();
    private final HBox               container;

    /**
     * @param pivotValues      ordered list of pivot-column names
     * @param rowHeaderWidth   width of the left row-header region
     * @param pivotColumnWidth width of each pivot data cell
     * @param cellHeight       height of this row
     * @param style            relation style (for insets; currently informational)
     */
    public CrosstabRow(List<String> pivotValues, double rowHeaderWidth,
                       double pivotColumnWidth, double cellHeight,
                       RelationStyle style) {
        getStyleClass().add(STYLE_CLASS);

        double snappedRowHdrW = Style.snap(rowHeaderWidth);
        double snappedColW    = Style.snap(pivotColumnWidth);
        double snappedH       = Style.snap(cellHeight);

        container = new HBox();

        // Row-header placeholder — will be populated by updateItem()
        Region rowHeader = new Region();
        rowHeader.getStyleClass().add("crosstab-row-header");
        rowHeader.setMinSize(snappedRowHdrW, snappedH);
        rowHeader.setPrefSize(snappedRowHdrW, snappedH);
        rowHeader.setMaxSize(snappedRowHdrW, snappedH);
        container.getChildren().add(rowHeader);

        // One data cell per pivot value
        for (int i = 0; i < pivotValues.size(); i++) {
            CrosstabCell cell = new CrosstabCell(snappedColW, snappedH);
            dataCells.add(cell);
            container.getChildren().add(cell);
        }

        getChildren().add(container);

        double totalWidth = Style.snap(snappedRowHdrW + snappedColW * pivotValues.size());
        setMinSize(totalWidth, snappedH);
        setPrefSize(totalWidth, snappedH);
        setMaxSize(totalWidth, snappedH);
    }

    /** @return number of pivot data cells in this row */
    public int getPivotCount() {
        return dataCells.size();
    }

    /**
     * Set the value of the pivot cell at {@code pivotIndex} from {@code item}.
     *
     * @param pivotIndex 0-based index into pivot values list
     * @param item       JSON node value to display (may be null)
     */
    public void updatePivotCell(int pivotIndex, JsonNode item) {
        if (pivotIndex >= 0 && pivotIndex < dataCells.size()) {
            dataCells.get(pivotIndex).updateItem(item);
        }
    }
}

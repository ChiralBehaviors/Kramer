// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.table;

import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 * A single data cell in a crosstab row, displaying the aggregated value at the
 * intersection of a row-header group and a pivot column.
 *
 * <p>Placed inside a {@link CrosstabRow}; one cell per pivot value.
 */
public class CrosstabCell extends Region {

    private static final String STYLE_CLASS = "crosstab-cell";

    private final Label  label;
    private final double cellWidth;
    private final double cellHeight;

    public CrosstabCell(double cellWidth, double cellHeight) {
        this.cellWidth  = Style.snap(cellWidth);
        this.cellHeight = Style.snap(cellHeight);

        getStyleClass().add(STYLE_CLASS);

        label = new Label();
        label.setMinSize(this.cellWidth, this.cellHeight);
        label.setPrefSize(this.cellWidth, this.cellHeight);
        label.setMaxSize(this.cellWidth, this.cellHeight);
        getChildren().add(label);

        setMinSize(this.cellWidth, this.cellHeight);
        setPrefSize(this.cellWidth, this.cellHeight);
        setMaxSize(this.cellWidth, this.cellHeight);
    }

    /**
     * Update the displayed value. A {@code null} node clears the cell.
     *
     * @param item the JSON node whose text representation is shown, or null
     */
    public void updateItem(JsonNode item) {
        if (item == null || item.isNull()) {
            label.setText("");
        } else {
            label.setText(item.asText());
        }
    }
}

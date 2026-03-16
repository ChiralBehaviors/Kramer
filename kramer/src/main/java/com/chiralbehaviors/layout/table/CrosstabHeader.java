// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.table;

import java.util.List;

import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * The fixed column-header bar for a crosstab layout.
 *
 * <p>Mirrors the header-outside-VirtualFlow pattern used by {@link NestedTable}:
 * the header is a plain {@link Region} placed above the VirtualFlow so that it
 * stays stationary while rows scroll.
 *
 * <p>The header consists of an optional row-header spacer (width = rowHeaderWidth)
 * followed by one label per pivot value (each with width = pivotColumnWidth).
 */
public class CrosstabHeader extends Region {

    private static final String STYLE_CLASS = "crosstab-header";

    private final List<String> pivotValues;
    private final HBox         container;

    /**
     * @param pivotValues      distinct pivot-column labels (insertion-ordered)
     * @param pivotColumnWidth width allocated to each pivot data column
     * @param height           height of the header bar
     * @param style            relation style (for insets; currently informational)
     */
    public CrosstabHeader(List<String> pivotValues, double pivotColumnWidth,
                          double height, RelationStyle style) {
        this.pivotValues = List.copyOf(pivotValues);

        getStyleClass().add(STYLE_CLASS);

        double snappedColW = Style.snap(pivotColumnWidth);
        double snappedH    = Style.snap(height);

        container = new HBox();
        container.setAlignment(Pos.CENTER_LEFT);

        for (String pv : this.pivotValues) {
            Label lbl = new Label(pv);
            lbl.setMinSize(snappedColW, snappedH);
            lbl.setPrefSize(snappedColW, snappedH);
            lbl.setMaxSize(snappedColW, snappedH);
            lbl.getStyleClass().add("crosstab-header-label");
            container.getChildren().add(lbl);
        }

        getChildren().add(container);

        double totalWidth = Style.snap(snappedColW * this.pivotValues.size());
        setMinSize(totalWidth, snappedH);
        setPrefSize(totalWidth, snappedH);
        setMaxSize(totalWidth, snappedH);
    }

    /** @return number of pivot columns */
    public int getPivotCount() {
        return pivotValues.size();
    }

    /** @return immutable list of pivot column labels */
    public List<String> getPivotValues() {
        return pivotValues;
    }
}

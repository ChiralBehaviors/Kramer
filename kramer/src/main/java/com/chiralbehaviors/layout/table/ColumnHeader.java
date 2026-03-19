/**
 * Copyright (c) 2017 Chiral Behaviors, LLC, all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chiralbehaviors.layout.table;

import java.util.List;
import java.util.function.Function;

import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.style.Style;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

/**
 * @author halhildebrand
 *
 */
public class ColumnHeader extends VBox {

    private Label sortArrow;

    public ColumnHeader() {
        getStyleClass().clear();
    }

    /**
     * Update the sort indicator arrow and CSS classes based on the current
     * sort state for this column.
     *
     * @param sortFields the current sortFields value from FieldState (may be null)
     * @param fieldName  the leaf field name for this column
     */
    public void updateSortIndicator(String sortFields, String fieldName) {
        if (sortArrow == null) {
            sortArrow = new Label();
            sortArrow.getStyleClass().add("sort-arrow");
            getChildren().add(sortArrow);
        }

        getStyleClass().removeAll("sorted-asc", "sorted-desc");

        if (sortFields != null && sortFields.equals(fieldName)) {
            sortArrow.setText("\u25B2");
            getStyleClass().add("sorted-asc");
        } else if (sortFields != null && sortFields.equals("-" + fieldName)) {
            sortArrow.setText("\u25BC");
            getStyleClass().add("sorted-desc");
        } else {
            sortArrow.setText("");
        }
    }

    public ColumnHeader(double width, double height, PrimitiveLayout layout) {
        this();
        if (layout.isUseVerticalHeader()) {
            // Paper Table 1: UseVerticalTableHeader — wrap rotated label
            // in a fixed-size Pane because setRotate() does not change
            // JavaFX layout bounds
            double labelW = layout.getLabelWidth();
            double labelH = layout.getLabelHeight();
            Label label = layout.label(labelW, labelH);
            label.setRotate(-90);

            // Wrapper fills the full column width; height is the label text width
            // (height parameter is unused — wrapper height comes from labelW)
            double visualWidth = width;
            double visualHeight = Style.snap(labelW);
            Pane wrapper = new Pane(label);
            wrapper.setMinSize(visualWidth, visualHeight);
            wrapper.setPrefSize(visualWidth, visualHeight);
            wrapper.setMaxSize(visualWidth, visualHeight);
            label.setTranslateX((visualWidth - labelW) / 2.0);
            label.setTranslateY((visualHeight - labelH) / 2.0);

            getChildren().add(wrapper);
        } else {
            getChildren().add(layout.label(width, height));
        }
    }

    public ColumnHeader(double width, double height, RelationLayout layout,
                        List<Function<Double, ColumnHeader>> nestedHeaders) {
        this();
        setMinHeight(height);
        setPrefHeight(height);
        setMaxHeight(height);
        setAlignment(Pos.CENTER);
        HBox nested = new HBox();
        double labelHeight = Style.snap(layout.getLabelHeight());
        double nestedHeight = Style.snap(height - labelHeight);
        getChildren().addAll(layout.label(width, labelHeight), nested);

        nestedHeaders.forEach(n -> {
            nested.getChildren()
                  .add(n.apply(nestedHeight));
        });
    }
}

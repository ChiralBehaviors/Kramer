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

import java.util.ArrayList;
import java.util.List;

import com.chiralbehaviors.layout.LayoutView;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.cell.VerticalCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * @author halhildebrand
 *
 */
public class NestedTable extends VerticalCell<NestedTable> {
    private static final String DEFAULT_STYLE         = "nested-table";
    private static final String SCHEMA_CLASS_TEMPLATE = "%s-nested-table";
    private static final String STYLE_SHEET           = "nested-table.css";

    public static List<JsonNode> itemsAsArray(JsonNode items) {
        List<JsonNode> itemArray = new ArrayList<>();
        SchemaNode.asArray(items)
                  .forEach(n -> itemArray.add(n));
        return itemArray;
    }

    private final VirtualFlow<NestedCell> rows;

    public NestedTable(int childCardinality, LayoutView layout,
                       FocusTraversal<?> parentTraversal, Style model,
                       RelationStyle style, boolean rootLevel) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE,
                                          layout.getCssClass()));
        Region header = layout.buildColumnHeader();
        double width = Style.snap(layout.getJustifiedTableColumnWidth()
                                  + style.getTableHorizontalInset());
        double height = Style.snap(layout.getHeight());

        rows = new NestedRow(Style.snap(height - layout.columnHeaderHeight()),
                             layout, childCardinality, parentTraversal, model,
                             style, rootLevel);

        getChildren().addAll(header, rows);
        model.apply(rows, layout.getNode());

        // Aggregate footer — root-level only (avoids height allocation issues in nested tables)
        if (rootLevel && "footer".equals(layout.getAggregatePosition())) {
            var aggResults = layout.getAggregateResults();
            if (aggResults != null && !aggResults.isEmpty()) {
                HBox footer = buildAggregateFooter(layout, aggResults);
                getChildren().add(footer);
            }
        }

        if (rootLevel) {
            // Root table fills viewport — VirtualFlow grows to fill VBox
            setMinWidth(width);
            setPrefWidth(width);
            setMaxWidth(width);
            VBox.setVgrow(rows, Priority.ALWAYS);
        } else {
            // Nested table — fixed size for N rows within parent
            setMinSize(width, height);
            setPrefSize(width, height);
            setMaxSize(width, height);
        }
    }

    public NestedTable(String field) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, SchemaPath.sanitize(field)));
        this.rows = null;
    }

    @Override
    public void activate() {
        if (rows != null) rows.activate();
    }

    @Override
    public void setFocus() {
        if (rows != null) rows.setFocus();
    }

    private static HBox buildAggregateFooter(LayoutView layout,
                                                java.util.Map<String, Object> aggResults) {
        HBox footer = new HBox();
        footer.getStyleClass().add("aggregate-footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        for (var child : layout.getChildren()) {
            double colWidth = Style.snap(child.getJustifiedWidth());
            String fieldName = child.getField();
            Object value = aggResults.get(fieldName);
            Label cell = new Label(value != null ? value.toString() : "");
            cell.getStyleClass().add("aggregate-cell");
            cell.setMinWidth(colWidth);
            cell.setPrefWidth(colWidth);
            cell.setMaxWidth(colWidth);
            footer.getChildren().add(cell);
        }
        return footer;
    }

    @Override
    public void updateItem(JsonNode item) {
        rows.getItems()
            .setAll(SchemaNode.asList(item));
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_EMPTY, item == null);
    }
}

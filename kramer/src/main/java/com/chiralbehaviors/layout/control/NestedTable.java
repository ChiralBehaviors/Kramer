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

package com.chiralbehaviors.layout.control;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.chiralbehaviors.layout.LayoutProvider;
import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Skin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 * @author halhildebrand
 *
 */
public class NestedTable extends JsonControl {

    private double                   rowHeight;
    private final ListView<JsonNode> rows;

    public NestedTable(int cardinality, RelationLayout layout) {
        getStyleClass().add(layout.getStyleClass());
        this.rows = buildRows(cardinality, layout);
        Region header = layout.buildColumnHeader();
        VBox frame = new VBox(header, rows);
        this.getChildren()
            .add(frame);
    }

    public Pair<Consumer<JsonNode>, Region> buildPrimitive(double rendered,
                                                           PrimitiveLayout layout) {
        JsonControl control = layout.buildControl(1);

        double width = layout.tableColumnWidth(layout.getJustifiedWidth());
        control.setMinSize(width, rendered);
        control.setMaxSize(width, rendered);

        return new Pair<>(node -> control.setItem(layout.extractFrom(node)),
                          control);
    }

    public Pair<Consumer<JsonNode>, Region> buildRelation(double rendered,
                                                          RelationLayout layout) {
        ListView<JsonNode> column = buildNestedRow(rendered, layout);
        return new Pair<>(node -> column.getItems()
                                        .setAll(itemsAsArray(layout.extractFrom(node))),
                          column);
    }

    public double getRowHeight() {
        return rowHeight;
    }

    @Override
    public void setItem(JsonNode items) {
        rows.getItems()
            .setAll(itemsAsArray(items));
    }

    public void setRowHeight(double rowHeight) {
        this.rowHeight = rowHeight;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new NestedTableSkin(this);
    }

    private Pair<Consumer<JsonNode>, Region> buildColumn(double rendered,
                                                         RelationLayout layout) {
        HBox cell = new HBox();
        cell.getStyleClass()
            .add(layout.getStyleClass());
        HBox.setHgrow(cell, Priority.ALWAYS);
        cell.setMinWidth(layout.getJustifiedWidth());
        cell.setPrefWidth(layout.getJustifiedWidth());
        cell.setMinHeight(rendered);
        cell.setMaxHeight(rendered);
        List<Consumer<JsonNode>> consumers = new ArrayList<>();
        Consumer<? super SchemaNode> action = child -> {
            Pair<Consumer<JsonNode>, Region> column = child.buildColumn(this,
                                                                        rendered);
            consumers.add(column.getKey());
            cell.getChildren()
                .add(column.getValue());
        };
        layout.forEach(action);
        return new Pair<>(node -> consumers.forEach(c -> {
            c.accept(node);
        }), cell);
    }

    private ListView<JsonNode> buildNestedRow(double rendered,
                                              RelationLayout layout) {
        int cardinality = layout.isSingular() ? 1
                                              : layout.getAverageCardinality();
        double calculatedHeight = layout.getHeight();
        double deficit = Math.max(0, rendered - calculatedHeight);
        double childDeficit = LayoutProvider.snap(Math.max(0, deficit
                                                              / cardinality));
        double extended = LayoutProvider.snap(layout.getRowHeight()
                                              + childDeficit);

        ListView<JsonNode> row = new ListView<>();
        layout.apply(row);

        row.setFixedCellSize(extended);
        row.setMinHeight(rendered);
        row.setMaxHeight(rendered);

        double width = layout.tableColumnWidth(layout.getJustifiedWidth());
        row.setMinWidth(width);
        row.setMaxWidth(width);

        row.setCellFactory(listView -> {
            ListCell<JsonNode> cell = buildRowCell(buildColumn(layout.baseRowCellHeight(extended),
                                                               layout));
            double cellWidth = layout.baseTableColumnWidth(width);

            cell.setMinWidth(cellWidth);
            cell.setMaxWidth(cellWidth);

            cell.setMinHeight(extended);
            cell.setMaxHeight(extended);

            return cell;
        });
        return row;
    }

    // Eventually will be controlled by CSS
    private ListCell<JsonNode> buildRowCell(Pair<Consumer<JsonNode>, Region> cell) {
        return new ListCell<JsonNode>() {
            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(JsonNode item, boolean empty) {
                if (item == getItem()) {
                    return;
                }
                super.updateItem(item, empty);
                if (empty) {
                    return;
                }
                if (item == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(cell.getValue());
                cell.getKey()
                    .accept(item);
            }
        };
    }

    private ListView<JsonNode> buildRows(int card, RelationLayout layout) {
        ListView<JsonNode> rows = new ListView<>();
        layout.apply(rows);

        double rowHeight = layout.getRowHeight();
        rows.setFixedCellSize(rowHeight);

        double width = layout.tableColumnWidth(layout.getJustifiedWidth());
        double height = layout.getHeight() - layout.getColumnHeaderHeight();

        rows.setPrefSize(width, height);

        rows.setCellFactory(listView -> {
            ListCell<JsonNode> cell = buildRowCell(buildColumn(layout.baseRowCellHeight(rowHeight),
                                                               layout));

            cell.setPrefSize(layout.getJustifiedWidth(), rowHeight);
            return cell;
        });
        return rows;
    }

    private List<JsonNode> itemsAsArray(JsonNode items) {
        List<JsonNode> itemArray = new ArrayList<>();
        SchemaNode.asArray(items)
                  .forEach(n -> itemArray.add(n));
        return itemArray;
    }
}

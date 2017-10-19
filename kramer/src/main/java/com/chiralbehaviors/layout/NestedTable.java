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

package com.chiralbehaviors.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
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
public class NestedTable extends Control {

    private double                   rowHeight;
    private final ListView<JsonNode> rows;

    public NestedTable(int cardinality, Relation relation, Layout layout) {
        getStyleClass().add(relation.getLabel());
        HBox header = buildHeader(relation, layout);
        this.rows = buildRows(cardinality, relation, layout);
        VBox frame = new VBox(header, rows);
        this.getChildren()
            .add(frame);
    }

    public Pair<Consumer<JsonNode>, Region> buildPrimitive(double rendered,
                                                           Primitive child,
                                                           Layout layout) {
        Control text = child.getLayout()
                            .buildControl(1);

        double width = child.getLayout()
                            .tableColumnWidth(child.getJustifiedWidth());

        text.setMinWidth(width);
        text.setMaxWidth(width);

        text.setMinHeight(rendered);
        text.setMaxHeight(rendered);

        return new Pair<>(node -> layout.setItemsOf(text,
                                                    child.extractFrom(node)),
                          text);
    }

    public Pair<Consumer<JsonNode>, Region> buildRelation(double rendered,
                                                          Relation child,
                                                          Layout layout) {
        ListView<JsonNode> column = buildNestedRow(child, rendered, layout);
        return new Pair<>(node -> column.getItems()
                                        .setAll(itemsAsArray(child.extractFrom(node))),
                          column);
    }

    public double getRowHeight() {
        return rowHeight;
    }

    public void setItems(JsonNode items) {
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
                                                         Relation relation,
                                                         Layout layout) {
        HBox cell = new HBox();
        cell.getStyleClass()
            .add(relation.getField());
        HBox.setHgrow(cell, Priority.ALWAYS);
        cell.setMinWidth(relation.getJustifiedWidth());
        cell.setPrefWidth(relation.getJustifiedWidth());
        cell.setMinHeight(rendered);
        cell.setMaxHeight(rendered);
        List<Consumer<JsonNode>> consumers = new ArrayList<>();
        relation.getChildren()
                .forEach(child -> {
                    Pair<Consumer<JsonNode>, Region> column = child.buildColumn(this,
                                                                                rendered,
                                                                                layout);
                    consumers.add(column.getKey());
                    cell.getChildren()
                        .add(column.getValue());
                });
        return new Pair<>(node -> consumers.forEach(c -> {
            c.accept(node);
        }), cell);
    }

    private HBox buildHeader(Relation relation, Layout layout) {
        HBox header = new HBox();
        header.getStyleClass()
              .add("header");
        return header;
    }

    private ListView<JsonNode> buildNestedRow(Relation relation,
                                              double rendered, Layout layout) {
        int cardinality = relation.isSingular() ? 1
                                                : relation.getAverageCardinality();
        double calculatedHeight = relation.getHeight();
        double deficit = Math.max(0, rendered - calculatedHeight);
        double childDeficit = Layout.snap(Math.max(0, deficit / cardinality));
        double extended = Layout.snap(relation.getRowHeight() + childDeficit);

        ListView<JsonNode> row = new ListView<>();
        layout.getModel()
              .apply(row, relation);

        row.setFixedCellSize(extended);
        row.setMinHeight(rendered);
        row.setMaxHeight(rendered);

        double width = relation.getLayout()
                               .tableColumnWidth(relation.getJustifiedWidth());
        row.setMinWidth(width);
        row.setMaxWidth(width);

        row.setCellFactory(listView -> {
            ListCell<JsonNode> cell = buildRowCell(buildColumn(relation.getLayout()
                                                                       .baseRowCellHeight(extended),
                                                               relation,
                                                               layout));
            double cellWidth = relation.getLayout()
                                       .baseTableColumnWidth(width);

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

    private ListView<JsonNode> buildRows(int card, Relation relation,
                                         Layout layout) {
        ListView<JsonNode> rows = new ListView<>();
        layout.getModel()
              .apply(rows, relation);

        double rowHeight = relation.getRowHeight();
        rows.setFixedCellSize(rowHeight);

        double width = relation.getLayout()
                               .tableColumnWidth(relation.getJustifiedWidth());
        rows.setMinWidth(width);
        rows.setMaxWidth(width);

        rows.setMinHeight(relation.getHeight());
        rows.setMaxHeight(relation.getHeight());

        rows.setCellFactory(listView -> {
            ListCell<JsonNode> cell = buildRowCell(buildColumn(relation.getLayout()
                                                                       .baseRowCellHeight(rowHeight),
                                                               relation,
                                                               layout));

            cell.setMinWidth(relation.getJustifiedWidth());
            cell.setPrefWidth(relation.getJustifiedWidth());

            cell.setMinHeight(rowHeight);
            cell.setMaxHeight(rowHeight);
            return cell;
        });
        return rows;
    }

    private List<JsonNode> itemsAsArray(JsonNode items) {
        List<JsonNode> itemArray = new ArrayList<>();
        Relation.asArray(items)
                .forEach(n -> itemArray.add(n));
        return itemArray;
    }
}

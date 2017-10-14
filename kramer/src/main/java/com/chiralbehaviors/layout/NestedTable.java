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
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Skin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 * @author halhildebrand
 *
 */
public class NestedTable extends Control {
    private final ListView<JsonNode> rows;

    public NestedTable(int cardinality, Relation relation, Layout layout) {
        HBox header = buildHeader(relation, layout);
        this.rows = buildRows(cardinality, relation, layout);
        VBox frame = new VBox(header, rows);
        this.getChildren()
            .add(frame);
    }

    private ListView<JsonNode> buildRows(int card, Relation relation,
                                         Layout layout) {
        ListView<JsonNode> row = new ListView<>();
        layout.getModel()
              .apply(row, relation);

        double rowHeight = relation.getRowHeight();
        row.setFixedCellSize(rowHeight);

        double width = relation.getJustifiedWidth()
                       + layout.getListCellHorizontalInset()
                       + layout.getListHorizontalInset();
        row.setMinWidth(width);
        row.setMaxWidth(width);

        row.setMinHeight(relation.getHeight());
        row.setMaxHeight(relation.getHeight());

        row.setCellFactory(listView -> {
            ListCell<JsonNode> cell = listCell(buildCell(rowHeight
                                                         - layout.getListCellVerticalInset(),
                                                         relation, layout));

            cell.setMinWidth(relation.getJustifiedWidth());
            cell.setMaxWidth(relation.getJustifiedWidth());

            cell.setMinHeight(rowHeight);
            cell.setMaxHeight(rowHeight);
            return cell;
        });
        return row;
    }

    public void setItems(JsonNode items) {
        rows.getItems()
            .setAll(itemsAsArray(items));
    }

    private List<JsonNode> itemsAsArray(JsonNode items) {
        List<JsonNode> itemArray = new ArrayList<>();
        Relation.asArray(items)
                .forEach(n -> itemArray.add(n));
        return itemArray;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new NestedTableSkin(this);
    }

    private Pair<Consumer<JsonNode>, Region> buildCell(double rendered,
                                                       Relation relation,
                                                       Layout layout) {
        HBox cell = new HBox();
        cell.setMinWidth(relation.getJustifiedWidth());
        cell.setMaxWidth(relation.getJustifiedWidth());
        cell.setMinHeight(rendered);
        cell.setMaxHeight(rendered);
        List<Consumer<JsonNode>> consumers = new ArrayList<>();
        relation.getChildren()
                .forEach(child -> {
                    Pair<Consumer<JsonNode>, Region> column = buildColumn(rendered,
                                                                          child,
                                                                          layout);
                    consumers.add(column.getKey());
                    cell.getChildren()
                        .add(column.getValue());
                });
        return new Pair<>(node -> consumers.forEach(c -> {
            c.accept(node);
        }), cell);
    }

    private Pair<Consumer<JsonNode>, Region> buildColumn(double rendered,
                                                         SchemaNode child,
                                                         Layout layout) {
        return child.isRelation() ? buildRelation(rendered, (Relation) child,
                                                  layout)
                                  : buildPrimitive(rendered, (Primitive) child,
                                                   layout);
    }

    private HBox buildHeader(Relation relation, Layout layout) {
        return new HBox();
    }

    private Pair<Consumer<JsonNode>, Region> buildPrimitive(double rendered,
                                                            Primitive child,
                                                            Layout layout) {
        Label text = new Label();
        text.setWrapText(true);
        text.setStyle("-fx-background-color: rgba(0,0,0,0.08),"
                      + "                    linear-gradient(#9a9a9a, #909090),"
                      + "                    white 0%;"
                      + "-fx-background-insets: 0 0 -1 0,0,1;"
                      + "-fx-background-radius: 5,5,4;"
                      + "-fx-padding: 3 20 3 20;" + "-fx-text-fill: #242d35;"
                      + "-fx-font-size: 14px;");

        double width = child.getJustifiedWidth()
                       + layout.getTextHorizontalInset();

        text.setMinWidth(width);
        text.setMaxWidth(width);

        text.setMinHeight(rendered);
        text.setMaxHeight(rendered);

        return new Pair<>(node -> layout.setItemsOf(text,
                                                    child.extractFrom(node)),
                          text);
    }

    private Pair<Consumer<JsonNode>, Region> buildRelation(double rendered,
                                                           Relation child,
                                                           Layout layout) {
        ListView<JsonNode> column = buildNestedRow(child, rendered, layout);
        return new Pair<>(node -> column.getItems()
                                        .setAll(itemsAsArray(child.extractFrom(node))),
                          column);
    }

    private ListView<JsonNode> buildNestedRow(Relation relation,
                                              double rendered, Layout layout) {
        int cardinality = relation.isSingular() ? 1
                                                : relation.getAverageCardinality();
        double calculatedHeight = (relation.getRowHeight() * cardinality)
                                  + layout.getListVerticalInset();
        double deficit = Math.max(0, rendered - calculatedHeight);
        double childDeficit = Layout.snap(Math.max(0, deficit / cardinality));
        double extended = Layout.snap(relation.getRowHeight() + childDeficit);

        ListView<JsonNode> row = new ListView<>();
        layout.getModel()
              .apply(row, relation);

        row.setFixedCellSize(extended);
        row.setMinHeight(rendered);
        row.setMaxHeight(rendered);

        double width = relation.getJustifiedWidth()
                       + layout.getListCellHorizontalInset()
                       + layout.getListHorizontalInset();
        row.setMinWidth(width);
        row.setMaxWidth(width);

        row.setCellFactory(listView -> {
            ListCell<JsonNode> cell = listCell(buildCell(extended
                                                         - layout.getListCellVerticalInset(),
                                                         relation, layout));
            double cellWidth = width - layout.getListHorizontalInset();

            cell.setMinWidth(cellWidth);
            cell.setMaxWidth(cellWidth);

            cell.setMinHeight(extended);
            cell.setMaxHeight(extended);

            return cell;
        });
        return row;
    }

    private ListCell<JsonNode> listCell(Pair<Consumer<JsonNode>, Region> cell) {
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
                cell.getValue()
                    .requestLayout();
            }
        };
    }
}

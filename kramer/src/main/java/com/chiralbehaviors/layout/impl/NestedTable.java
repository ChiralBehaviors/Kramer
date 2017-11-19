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

package com.chiralbehaviors.layout.impl;

import static com.chiralbehaviors.layout.LayoutProvider.snap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.flowless.Cell;
import com.chiralbehaviors.layout.flowless.FlyAwayScrollPane;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * @author halhildebrand
 *
 */
public class NestedTable extends VBox implements LayoutCell<NestedTable> {
    private final ObservableList<JsonNode> items = FXCollections.observableArrayList();

    public NestedTable(int cardinality, RelationLayout layout) {
        Region header = layout.buildColumnHeader();
        getChildren().addAll(header, buildRows(cardinality, layout));
        setMinWidth(layout.getJustifiedColumnWidth());
        setPrefWidth(layout.getJustifiedColumnWidth());
        setMaxWidth(layout.getJustifiedColumnWidth());
    }

    public LayoutCell<? extends Region> buildPrimitive(double rendered,
                                                       PrimitiveLayout layout) {
        LayoutCell<? extends Region> control = layout.buildControl(1);
        control.getNode()
               .setMinSize(layout.getJustifiedWidth(), rendered);
        control.getNode()
               .setPrefSize(layout.getJustifiedWidth(), rendered);
        control.getNode()
               .setMaxSize(layout.getJustifiedWidth(), rendered);
        return new LayoutCell<Region>() {
            @Override
            public Region getNode() {
                return control.getNode();
            }

            @Override
            public boolean isReusable() {
                return true;
            }

            @Override
            public void updateItem(JsonNode item) {
                control.updateItem(layout.extractFrom(item));
            }
        };
    }

    public LayoutCell<? extends Region> buildRelation(double rendered,
                                                      RelationLayout layout) {
        ObservableList<JsonNode> nestedItems = FXCollections.observableArrayList();
        int cardinality = layout.resolvedCardinality();
        double deficit = rendered - layout.getHeight();
        double childDeficit = deficit / cardinality;
        double extended = snap(layout.getRowHeight() + childDeficit);
        double cellHeight = layout.baseRowCellHeight(extended);
        VirtualFlow<JsonNode, Cell<JsonNode, ?>> row = VirtualFlow.createVertical(layout.getJustifiedColumnWidth(),
                                                                                  cellHeight,
                                                                                  nestedItems,
                                                                                  cell(cellHeight,
                                                                                       layout));
        double width = layout.getJustifiedColumnWidth();
        row.setMinSize(width, rendered);
        row.setPrefSize(width, rendered);
        row.setMaxSize(width, rendered);

        Region scroll = new FlyAwayScrollPane<>(row);

        return new LayoutCell<Region>() {

            @Override
            public Region getNode() {
                return scroll;
            }

            @Override
            public boolean isReusable() {
                return true;
            }

            @Override
            public String toString() {
                return String.format("Cell[%s]", layout.getField());
            }

            @Override
            public void updateItem(JsonNode item) {
                nestedItems.setAll(itemsAsArray(layout.extractFrom(item)));
            }
        };

    }

    @Override
    public NestedTable getNode() {
        return this;
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    @Override
    public void updateItem(JsonNode item) {
        items.setAll(SchemaNode.asList(item));
    }

    private Region buildRows(int card, RelationLayout layout) {

        double cellHeight = layout.baseRowCellHeight(layout.getRowHeight());
        VirtualFlow<JsonNode, Cell<JsonNode, ?>> rows = VirtualFlow.createVertical(layout.getJustifiedColumnWidth(),
                                                                                   cellHeight,
                                                                                   items,
                                                                                   cell(cellHeight,
                                                                                        layout));

        double width = layout.getJustifiedColumnWidth();
        double height = snap(layout.getHeight()
                             - layout.getColumnHeaderHeight());

        rows.setMinSize(width, height);
        rows.setPrefSize(width, height);
        rows.setMaxSize(width, height);

        Region scroll = new FlyAwayScrollPane<>(rows);
        scroll.setMinSize(width, height);
        scroll.setPrefSize(width, height);
        scroll.setMaxSize(width, height);
        return scroll;
    }

    private Function<JsonNode, Cell<JsonNode, ?>> cell(double rendered,
                                                       RelationLayout layout) {
        return item -> {
            Cell<JsonNode, ?> column = buildColumn(rendered, layout);
            column.updateItem(item);
            return column;
        };
    }

    private List<JsonNode> itemsAsArray(JsonNode items) {
        List<JsonNode> itemArray = new ArrayList<>();
        SchemaNode.asArray(items)
                  .forEach(n -> itemArray.add(n));
        return itemArray;
    }

    private Cell<JsonNode, Region> buildColumn(double rendered,
                                               RelationLayout layout) {
        HBox cell = new HBox();
        cell.getStyleClass()
            .add(layout.getStyleClass());
        cell.setMinSize(layout.getJustifiedWidth(), rendered);
        cell.setPrefSize(layout.getJustifiedWidth(), rendered);
        cell.setMaxSize(layout.getJustifiedWidth(), rendered);
        List<LayoutCell<? extends Region>> nested = new ArrayList<>();
        layout.forEach(child -> {
            LayoutCell<? extends Region> column = child.buildColumn(rendered,
                                                                    this);
            nested.add(column);
            Region control = column.getNode();
            cell.getChildren()
                .add(control);
        });

        return new Cell<JsonNode, Region>() {

            @Override
            public Region getNode() {
                return cell;
            }

            @Override
            public boolean isReusable() {
                return true;
            }

            @Override
            public String toString() {
                return String.format("Cell[%s]", layout.getField());
            }

            @Override
            public void updateItem(JsonNode item) {
                nested.forEach(c -> c.updateItem(item));
            }
        };
    }
}

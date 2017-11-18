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
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import com.chiralbehaviors.layout.flowless.Cell;
import com.chiralbehaviors.layout.flowless.FlyAwayScrollPane;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * @author halhildebrand
 *
 */
public class Outline implements Cell<JsonNode, Region> {
    private final ObservableList<JsonNode>           items = FXCollections.observableArrayList();

    private VirtualFlow<JsonNode, Cell<JsonNode, ?>> list;

    private final StackPane                          root;

    public Outline(double height, Collection<ColumnSet> columnSets,
                   Function<JsonNode, JsonNode> extractor,
                   int averageCardinality, RelationLayout layout) {
        double cellHeight = layout.outlineCellHeight(columnSets.stream()
                                                               .mapToDouble(cs -> cs.getCellHeight())
                                                               .sum());
        Function<JsonNode, Cell<JsonNode, ?>> cell = listCell(columnSets,
                                                              averageCardinality,
                                                              layout.baseOutlineCellHeight(cellHeight),
                                                              extractor,
                                                              layout);
        list = VirtualFlow.createVertical(layout.getJustifiedWidth(),
                                          cellHeight, items,
                                          jsonNode -> cell.apply(jsonNode));
        list.setMinWidth(layout.getJustifiedColumnWidth());
        list.setPrefWidth(layout.getJustifiedColumnWidth());
        list.setMaxWidth(layout.getJustifiedColumnWidth());

        list.setMinHeight(height);
        list.setPrefHeight(height);
        list.setMaxHeight(height);

        Region pane = new FlyAwayScrollPane<>(list);
        StackPane.setAlignment(pane, Pos.TOP_LEFT);
        root = new StackPane(pane);
    }

    @Override
    public Region getNode() {
        return root;
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    @Override
    public void updateItem(JsonNode item) {
        items.setAll(SchemaNode.asList(item));
    }

    protected Cell<JsonNode, Region> build(Column c, int cardinality,
                                           Function<JsonNode, JsonNode> extractor,
                                           double labelWidth,
                                           double cellHeight) {

        VBox column = new VBox();
        column.getStyleClass()
              .add("column");
        column.setMinSize(c.getWidth(), cellHeight);
        column.setMaxSize(c.getWidth(), cellHeight);
        column.setPrefSize(c.getWidth(), cellHeight);
        List<Cell<JsonNode, Region>> cells = new ArrayList<>();
        c.getFields()
         .forEach(field -> {
             Cell<JsonNode, Region> cell = field.outlineElement(cardinality,
                                                                labelWidth,
                                                                extractor,
                                                                c.getWidth());
             cells.add(cell);
             column.getChildren()
                   .add(cell.getNode());
         });
        return new Cell<JsonNode, Region>() {

            @Override
            public Region getNode() {
                return column;
            }

            @Override
            public boolean isReusable() {
                return true;
            }

            @Override
            public void updateItem(JsonNode item) {
                cells.forEach(m -> m.updateItem(item));
            }
        };
    }

    protected Cell<JsonNode, Region> build(List<Column> columns,
                                           int cardinality, double cellHeight,
                                           Function<JsonNode, JsonNode> extractor,
                                           double labelWidth) {
        HBox span = new HBox();
        span.setPrefHeight(cellHeight);
        List<Cell<JsonNode, Region>> controls = new ArrayList<>();
        columns.forEach(c -> {
            Cell<JsonNode, Region> column = build(c, cardinality, extractor,
                                                  labelWidth, cellHeight);
            controls.add(column);
            span.getChildren()
                .add(column.getNode());
        });
        return new Cell<JsonNode, Region>() {

            @Override
            public Region getNode() {
                return span;
            }

            @Override
            public boolean isReusable() {
                // TODO Auto-generated method stub
                return Cell.super.isReusable();
            }

            @Override
            public void updateItem(JsonNode item) {
                controls.forEach(c -> c.updateItem(item));
            }
        };
    }

    protected Function<JsonNode, Cell<JsonNode, ?>> listCell(Collection<ColumnSet> columnSets,
                                                             int averageCardinality,
                                                             double cellHeight,
                                                             Function<JsonNode, JsonNode> extractor,
                                                             RelationLayout layout) {
        return item -> {
            List<Cell<JsonNode, Region>> controls = new ArrayList<>();
            VBox cell = new VBox();
            cell.setMinHeight(cellHeight);
            cell.setMaxHeight(cellHeight);
            cell.setMinWidth(layout.getJustifiedWidth());
            cell.setPrefWidth(layout.getJustifiedWidth());
            cell.setMaxWidth(layout.getJustifiedWidth());
            columnSets.forEach(cs -> {
                Cell<JsonNode, Region> master = build(cs.getColumns(),
                                                      averageCardinality,
                                                      cs.getCellHeight(),
                                                      extractor,
                                                      layout.getLabelWidth());
                controls.add(master);
                VBox.setVgrow(master.getNode(), Priority.ALWAYS);
                cell.getChildren()
                    .add(master.getNode());
            });
            controls.forEach(child -> child.updateItem(item));
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
                    return cell.toString();
                }

                @Override
                public void updateItem(JsonNode item) {
                    controls.forEach(child -> child.updateItem(item));
                }
            };
        };
    }

}

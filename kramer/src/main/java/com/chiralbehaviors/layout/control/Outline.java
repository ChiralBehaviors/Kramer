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
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;

import com.chiralbehaviors.layout.Column;
import com.chiralbehaviors.layout.ColumnSet;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Skin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 * @author halhildebrand
 *
 */
public class Outline extends JsonControl {
    private final ObservableList<JsonNode>                items = FXCollections.observableArrayList();
    private VirtualFlow<JsonNode, Cell<JsonNode, Region>> list;

    public Outline(String styleClass) {
        getStyleClass().add(styleClass);
    }

    public Outline build(double height, Collection<ColumnSet> columnSets,
                         Function<JsonNode, JsonNode> extractor,
                         int averageCardinality, RelationLayout layout) {
        double cellHeight = layout.outlineCellHeight(columnSets.stream()
                                                               .mapToDouble(cs -> cs.getCellHeight())
                                                               .sum());
        Function<JsonNode, Cell<JsonNode, Region>> cell = listCell(columnSets,
                                                                   averageCardinality,
                                                                   layout.baseOutlineCellHeight(cellHeight),
                                                                   extractor,
                                                                   layout);
        list = VirtualFlow.createVertical(items,
                                          jsonNode -> cell.apply(jsonNode));
        list.setMinWidth(layout.getJustifiedColumnWidth());
        list.setPrefWidth(layout.getJustifiedColumnWidth());
        list.setMaxWidth(layout.getJustifiedColumnWidth());
        
        list.setMinHeight(height);
        list.setPrefHeight(height);
        list.setMaxHeight(height);
        StackPane.setAlignment(list, Pos.TOP_LEFT);
        getChildren().add(new StackPane(list));
        return this;
    }

    @Override
    public void setItem(JsonNode item) {
        items.setAll(SchemaNode.asList(item));
    }

    protected Consumer<JsonNode> build(Column c, int cardinality,
                                       Function<JsonNode, JsonNode> extractor,
                                       double labelWidth, VBox column) {
        List<Consumer<JsonNode>> controls = new ArrayList<>();
        c.getFields()
         .forEach(field -> {
             Pair<Consumer<JsonNode>, Parent> master = field.outlineElement(cardinality,
                                                                            labelWidth,
                                                                            extractor,
                                                                            c.getWidth());
             controls.add(master.getKey());
             column.getChildren()
                   .add(master.getValue());
         });
        return item -> controls.forEach(m -> m.accept(item));
    }

    protected Pair<Consumer<JsonNode>, Parent> build(List<Column> columns,
                                                     int cardinality,
                                                     double cellHeight,
                                                     Function<JsonNode, JsonNode> extractor,
                                                     double labelWidth) {
        HBox span = new HBox();
        span.setPrefHeight(cellHeight);
        List<Consumer<JsonNode>> controls = new ArrayList<>();
        columns.forEach(c -> {
            VBox column = new VBox();
            column.getStyleClass()
                  .add("column");
            column.setMinSize(c.getWidth(), cellHeight);
            column.setMaxSize(c.getWidth(), cellHeight);
            column.setPrefSize(c.getWidth(), cellHeight);
            controls.add(build(c, cardinality, extractor, labelWidth, column));
            span.getChildren()
                .add(column);
        });
        return new Pair<>(item -> controls.forEach(c -> c.accept(item)), span);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new OutlineSkin(this);
    }

    protected Function<JsonNode, Cell<JsonNode, Region>> listCell(Collection<ColumnSet> columnSets,
                                                                  int averageCardinality,
                                                                  double cellHeight,
                                                                  Function<JsonNode, JsonNode> extractor,
                                                                  RelationLayout layout) {
        return item -> {
            List<Consumer<JsonNode>> controls = new ArrayList<>();
            VBox cell = new VBox();
            cell.setMinHeight(cellHeight);
            cell.setMaxHeight(cellHeight);
            cell.setMinWidth(layout.getJustifiedWidth());
            cell.setPrefWidth(layout.getJustifiedWidth());
            cell.setMaxWidth(layout.getJustifiedWidth());
            columnSets.forEach(cs -> {
                Pair<Consumer<JsonNode>, Parent> master = build(cs.getColumns(),
                                                                averageCardinality,
                                                                cs.getCellHeight(),
                                                                extractor,
                                                                layout.getLabelWidth());
                controls.add(master.getKey());
                Parent control = master.getValue();
                VBox.setVgrow(control, Priority.ALWAYS);
                cell.getChildren()
                    .add(control);
            });
            controls.forEach(child -> child.accept(item));
            return new Cell<JsonNode, Region>() {

                @Override
                public Region getNode() {
                    return cell;
                }

                @Override
                public String toString() {
                    return cell.toString();
                }

                @Override
                public boolean isReusable() {
                    return true;
                }

                @Override
                public void updateItem(JsonNode item) {
                    controls.forEach(child -> child.accept(item));
                }
            };
        };
    }

}

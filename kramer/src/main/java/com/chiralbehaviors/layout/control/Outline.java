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

import com.chiralbehaviors.layout.Column;
import com.chiralbehaviors.layout.ColumnSet;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Skin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Pair;

/**
 * @author halhildebrand
 *
 */
public class Outline extends JsonControl {
    private final ListView<JsonNode> list;
    @SuppressWarnings("unused")
    private final Relation           relation;

    public Outline(Relation relation) {
        getStyleClass().add(relation.getField());
        list = new ListView<>();
        getChildren().add(list);
        this.relation = relation;
    }

    public Outline build(double height, Collection<ColumnSet> columnSets,
                         Function<JsonNode, JsonNode> extractor,
                         int averageCardinality, RelationLayout layout) {

        double cellHeight = layout.outlineCellHeight(columnSets.stream()
                                                               .mapToDouble(cs -> cs.getCellHeight())
                                                               .sum());
        layout.apply(list);
        list.setPrefHeight(height);
        list.setFixedCellSize(cellHeight);
        double width = layout.outlineWidth(layout.getJustifiedWidth());
        list.setMinWidth(width);
        list.setMaxWidth(width);
        list.setCellFactory(c -> {
            ListCell<JsonNode> cell = listCell(columnSets, averageCardinality,
                                               layout.baseOutlineCellHeight(cellHeight),
                                               extractor, layout);
            layout.apply(cell);
            return cell;
        });
        list.setPlaceholder(new Text());
        return this;
    }

    public Pair<Consumer<JsonNode>, Parent> build(List<Column> columns,
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
            column.setPrefHeight(cellHeight);
            column.setPrefWidth(c.getWidth());
            controls.add(build(c, cardinality, extractor, labelWidth, column));
            span.getChildren()
                .add(column);
        });
        return new Pair<>(item -> controls.forEach(c -> c.accept(item)), span);
    }

    @Override
    public void setItem(JsonNode item) {
        list.getItems()
            .setAll(SchemaNode.asList(item));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new OutlineSkin(this);
    }

    private Consumer<JsonNode> build(Column c, int cardinality,
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

    private ListCell<JsonNode> listCell(Collection<ColumnSet> columnSets,
                                        int averageCardinality,
                                        double cellHeight,
                                        Function<JsonNode, JsonNode> extractor,
                                        RelationLayout layout) {
        return new ListCell<JsonNode>() {
            VBox                     cell;
            List<Consumer<JsonNode>> controls = new ArrayList<>();
            {
                itemProperty().addListener((obs, oldItem, newItem) -> {
                    if (newItem != null) {
                        if (cell == null) {
                            initialize(extractor, layout);
                        }
                        setGraphic(cell);
                    }
                });
                emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                    if (isEmpty) {
                        setGraphic(null);
                    } else {
                        setGraphic(cell);
                    }
                });
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(JsonNode item, boolean empty) {
                if (item == getItem()) {
                    return;
                }
                super.updateItem(item, empty);
                super.setText(null);
                if (empty) {
                    super.setGraphic(null);
                    return;
                }
                controls.forEach(child -> child.accept(item));
            }

            private void initialize(Function<JsonNode, JsonNode> extractor,
                                    RelationLayout layout) {
                cell = new VBox();
                cell.setMinHeight(cellHeight);
                cell.setPrefHeight(cellHeight);
                cell.setMinWidth(0);
                cell.setPrefWidth(1);
                columnSets.forEach(cs -> {
                    Pair<Consumer<JsonNode>, Parent> master = build(cs.getColumns(),
                                                                    averageCardinality,
                                                                    cs.getCellHeight(),
                                                                    extractor,
                                                                    cs.getLabelWidth());
                    controls.add(master.getKey());
                    Parent control = master.getValue();
                    VBox.setVgrow(control, Priority.ALWAYS);
                    cell.getChildren()
                        .add(control);
                });
            }
        };
    }

}

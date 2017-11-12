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
import java.util.function.Function;

import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;

import com.chiralbehaviors.layout.LayoutProvider;
import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Skin;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 * @author halhildebrand
 *
 */
public class NestedTable extends JsonControl {
    private final ObservableList<JsonNode>           items = FXCollections.observableArrayList();
    private VirtualFlow<JsonNode, Cell<JsonNode, ?>> rows;

    public NestedTable(RelationLayout layout) {
        getStyleClass().add(layout.getStyleClass());
    }

    public JsonControl build(int cardinality, RelationLayout layout) {
        Region header = layout.buildColumnHeader();
        VBox frame = new VBox(header, buildRows(cardinality, layout));
        frame.setMinWidth(layout.getJustifiedColumnWidth());
        frame.setPrefWidth(layout.getJustifiedColumnWidth());
        frame.setMaxWidth(layout.getJustifiedColumnWidth());

        AnchorPane.setLeftAnchor(frame, 0d);
        AnchorPane.setRightAnchor(frame, 0d);
        AnchorPane.setTopAnchor(frame, 0d);
        AnchorPane.setBottomAnchor(frame, 0d);

        getChildren().add(new AnchorPane(frame));
        return this;
    }

    public Pair<Consumer<JsonNode>, Region> buildPrimitive(double rendered,
                                                           PrimitiveLayout layout) {
        JsonControl control = layout.buildControl(1);
        double width = layout.getJustifiedWidth();
        control.setMinWidth(width);
        control.setPrefWidth(width);
        control.setMaxWidth(width);
        return new Pair<>(node -> control.setItem(layout.extractFrom(node)),
                          control);
    }

    public Pair<Consumer<JsonNode>, Region> buildRelation(double rendered,
                                                          RelationLayout layout) {
        Pair<ObservableList<JsonNode>, VirtualFlow<JsonNode, Cell<JsonNode, Region>>> column = buildNestedRow(rendered,
                                                                                                              layout);

        VirtualFlow<JsonNode, Cell<JsonNode, Region>> row = column.getValue();
        StackPane.setAlignment(row, Pos.TOP_LEFT);
        return new Pair<>(node -> column.getKey()
                                        .setAll(itemsAsArray(layout.extractFrom(node))),
                          new StackPane(row));
    }

    @Override
    public void setItem(JsonNode item) {
        items.setAll(itemsAsArray(item));
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
        List<Consumer<JsonNode>> consumers = new ArrayList<>();
        layout.forEach(child -> {
            Pair<Consumer<JsonNode>, Region> column = child.buildColumn(this,
                                                                        rendered);
            consumers.add(column.getKey());
            Region control = column.getValue();
            cell.getChildren()
                .add(control);
        });
        return new Pair<>(node -> consumers.forEach(c -> {
            c.accept(node);
        }), cell);
    }

    private Pair<ObservableList<JsonNode>, VirtualFlow<JsonNode, Cell<JsonNode, Region>>> buildNestedRow(double rendered,
                                                                                                         RelationLayout layout) {
        ObservableList<JsonNode> nestedItems = FXCollections.observableArrayList();
        int cardinality = layout.resolvedCardinality();
        double calculatedHeight = layout.getHeight();
        double deficit = Math.max(0, rendered - calculatedHeight);
        double childDeficit = LayoutProvider.snap(Math.max(0, deficit
                                                              / cardinality));
        double extended = LayoutProvider.snap(layout.getRowHeight()
                                              + childDeficit);
        VirtualFlow<JsonNode, Cell<JsonNode, Region>> row = VirtualFlow.createVertical(nestedItems,
                                                                                       item -> cell(layout.baseRowCellHeight(extended),
                                                                                                    layout).apply(item));
        double width = layout.getJustifiedColumnWidth();
        row.setMinSize(width, rendered);
        row.setPrefSize(width, rendered);
        row.setMaxSize(width, rendered);

        return new Pair<>(nestedItems, row);
    }

    private StackPane buildRows(int card, RelationLayout layout) {

        double rowHeight = layout.getRowHeight();
        rows = VirtualFlow.createVertical(items,
                                          item -> cell(layout.baseRowCellHeight(rowHeight),
                                                       layout).apply(item));

        double width = layout.getJustifiedColumnWidth();
        double height = layout.getHeight() - layout.getColumnHeaderHeight();

        rows.setMinSize(width, height);
        rows.setPrefSize(width, height);
        rows.setMaxSize(width, height);

        StackPane.setAlignment(rows, Pos.CENTER_LEFT);
        StackPane pane = new StackPane(rows);
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        return pane;
    }

    private Function<JsonNode, Cell<JsonNode, Region>> cell(double rendered,
                                                            RelationLayout layout) {
        return item -> {
            return new Cell<JsonNode, Region>() {
                Pair<Consumer<JsonNode>, Region> column = buildColumn(rendered,
                                                                      layout);
                {
                    column.getKey()
                          .accept(item);
                }

                @Override
                public Region getNode() {
                    return column.getValue();
                }

                @Override
                public boolean isReusable() {
                    return true;
                }

                @Override
                public String toString() {
                    return column.getValue()
                                 .toString();
                }

                @Override
                public void updateItem(JsonNode item) {
                    column.getKey()
                          .accept(item);
                }
            };
        };
    }

    private List<JsonNode> itemsAsArray(JsonNode items) {
        List<JsonNode> itemArray = new ArrayList<>();
        SchemaNode.asArray(items)
                  .forEach(n -> itemArray.add(n));
        return itemArray;
    }
}

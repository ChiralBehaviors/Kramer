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

import static com.chiralbehaviors.layout.LayoutProvider.snap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.flowless.Cell;
import com.chiralbehaviors.layout.flowless.FlyAwayScrollPane;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 * @author halhildebrand
 *
 */
public class NestedTable implements Cell<JsonNode, Region> {
    private final VBox                     frame;
    private final ObservableList<JsonNode> items = FXCollections.observableArrayList();

    public NestedTable(int cardinality, RelationLayout layout) {
        Region header = layout.buildColumnHeader();
        frame = new VBox(header, buildRows(cardinality, layout));
        frame.setMinWidth(layout.getJustifiedColumnWidth());
        frame.setPrefWidth(layout.getJustifiedColumnWidth());
        frame.setMaxWidth(layout.getJustifiedColumnWidth());
    }

    public Cell<JsonNode, Region> buildPrimitive(double rendered,
                                                 PrimitiveLayout layout) {
        Cell<JsonNode, Region> control = layout.buildControl(1);
        control.getNode()
               .setMinSize(layout.getJustifiedWidth(), rendered);
        control.getNode()
               .setPrefSize(layout.getJustifiedWidth(), rendered);
        control.getNode()
               .setMaxSize(layout.getJustifiedWidth(), rendered);
        return control;
    }

    public Pair<Consumer<JsonNode>, Region> buildRelation(double rendered,
                                                          RelationLayout layout) {
        Pair<ObservableList<JsonNode>, Region> column = buildNestedRow(rendered,
                                                                       layout);

        Region row = column.getValue();
        StackPane.setAlignment(row, Pos.TOP_LEFT);
        return new Pair<>(node -> column.getKey()
                                        .setAll(itemsAsArray(layout.extractFrom(node))),
                          new StackPane(row));
    }

    @Override
    public Region getNode() {
        return frame;
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    @Override
    public void updateItem(JsonNode item) {
        items.setAll(SchemaNode.asList(item));
    }

    private Pair<ObservableList<JsonNode>, Region> buildNestedRow(double rendered,
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
                                                                                  item -> cell(cellHeight,
                                                                                               layout).apply(item));
        double width = layout.getJustifiedColumnWidth();
        row.setMinSize(width, rendered);
        row.setPrefSize(width, rendered);
        row.setMaxSize(width, rendered);
        row.show(1);

        return new Pair<>(nestedItems, new FlyAwayScrollPane<>(row));
    }

    private Region buildRows(int card, RelationLayout layout) {

        double cellHeight = layout.baseRowCellHeight(layout.getRowHeight());
        VirtualFlow<JsonNode, Cell<JsonNode, ?>> rows = VirtualFlow.createVertical(layout.getJustifiedColumnWidth(),
                                                                                   cellHeight,
                                                                                   items,
                                                                                   item -> cell(cellHeight,
                                                                                                layout).apply(item));

        double width = layout.getJustifiedColumnWidth();
        double height = snap(layout.getHeight()
                             - layout.getColumnHeaderHeight());

        rows.setMinSize(width, height);
        rows.setPrefSize(width, height);
        rows.setMaxSize(width, height);

        StackPane.setAlignment(rows, Pos.TOP_LEFT);
        Region scroll = new FlyAwayScrollPane<>(rows);
        scroll.setMinSize(width, height);
        scroll.setPrefSize(width, height);
        scroll.setMaxSize(width, height);
        return scroll;
    }

    private Function<JsonNode, Cell<JsonNode, ?>> cell(double rendered,
                                                       RelationLayout layout) {
        return item -> {
            Cell<JsonNode, ?> column = layout.buildColumn(rendered);
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
}

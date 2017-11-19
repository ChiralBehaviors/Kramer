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

import java.util.Collection;
import java.util.function.Function;

import com.chiralbehaviors.layout.ColumnSet;
import com.chiralbehaviors.layout.RelationLayout;
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

/**
 * @author halhildebrand
 *
 */
public class Outline extends StackPane implements LayoutCell<Outline> {
    private final ObservableList<JsonNode> items = FXCollections.observableArrayList();

    public Outline(double height, Collection<ColumnSet> columnSets,
                   Function<JsonNode, JsonNode> extractor,
                   int averageCardinality, RelationLayout layout) {
        double cellHeight = layout.outlineCellHeight(columnSets.stream()
                                                               .mapToDouble(cs -> cs.getCellHeight())
                                                               .sum());
        Function<JsonNode, Cell<JsonNode, ?>> cell = item -> {
            OutlineCell outlineCell = new OutlineCell(columnSets,
                                                      averageCardinality,
                                                      layout.baseOutlineCellHeight(cellHeight),
                                                      extractor, layout);
            outlineCell.updateItem(item);
            return outlineCell;
        };
        VirtualFlow<JsonNode, Cell<JsonNode, ?>> list = VirtualFlow.createVertical(layout.getJustifiedWidth(),
                                                                                   cellHeight,
                                                                                   items,
                                                                                   jsonNode -> cell.apply(jsonNode));
        list.setMinSize(layout.getJustifiedColumnWidth(), height);
        list.setPrefSize(layout.getJustifiedColumnWidth(), height);
        list.setMaxSize(layout.getJustifiedColumnWidth(), height);

        Region pane = new FlyAwayScrollPane<>(list);
        StackPane.setAlignment(pane, Pos.TOP_LEFT);
        getChildren().add(pane);
    }

    @Override
    public Outline getNode() {
        return this;
    }

    @Override
    public void updateItem(JsonNode item) {
        items.setAll(SchemaNode.asList(item));
    }
}

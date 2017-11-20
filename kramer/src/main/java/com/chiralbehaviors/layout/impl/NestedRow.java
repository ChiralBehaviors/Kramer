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

import java.util.function.Function;

import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.AnchorPane;

/**
 * @author halhildebrand
 *
 */
public class NestedRow extends AnchorPane implements LayoutCell<NestedRow> {
    private static final String DEFAULT_STYLE = "nested-row";

    private static double cellHeight(double rendered, RelationLayout layout) {
        int cardinality = layout.resolvedCardinality();
        double deficit = rendered - layout.getHeight();
        double childDeficit = deficit / cardinality;
        double extended = snap(layout.getRowHeight() + childDeficit);
        return layout.baseRowCellHeight(extended);
    }

    private final Function<JsonNode, JsonNode> extractor;

    private final ObservableList<JsonNode>     nestedItems;

    {
        styleFocus(this, DEFAULT_STYLE);
    }

    public NestedRow(double rendered, RelationLayout layout) {
        getStyleClass().addAll(layout.getField());
        double cellHeight = cellHeight(rendered, layout);
        ObservableList<JsonNode> items = FXCollections.observableArrayList();
        VirtualFlow<JsonNode, NestedCell> row = VirtualFlow.createVertical(layout.getJustifiedColumnWidth(),
                                                                           cellHeight,
                                                                           items,
                                                                           item1 -> {
                                                                               NestedCell cell = new NestedCell(cellHeight,
                                                                                                                layout);
                                                                               cell.updateItem(item1);
                                                                               return cell;
                                                                           });
        this.extractor = item -> layout.extractFrom(item);
        this.nestedItems = items;
        double width = layout.getJustifiedColumnWidth();
        row.setMinSize(width, rendered);
        row.setPrefSize(width, rendered);
        row.setMaxSize(width, rendered);
        getChildren().add(row);
    }

    @Override
    public NestedRow getNode() {
        return this;
    }

    @Override
    public void updateItem(JsonNode item) {
        nestedItems.setAll(NestedTable.itemsAsArray(extractor.apply(item)));
    }
}

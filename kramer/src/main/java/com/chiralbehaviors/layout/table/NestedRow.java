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

package com.chiralbehaviors.layout.table;

import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.cell.HorizontalCell;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;

/**
 * @author halhildebrand
 *
 */
public class NestedRow extends HorizontalCell<NestedRow> {
    private static final String               DEFAULT_STYLE = "nested-row";
    private static final String               STYLE_SHEET   = "nested-row.css";

    private VirtualFlow<JsonNode, NestedCell> row;

    public NestedRow(double rendered, RelationLayout layout,
                     int childCardinality) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().addAll(layout.getField());
        double deficit = rendered - layout.getHeight();
        double childDeficit = deficit / childCardinality;
        double extended = layout.getRowHeight() + childDeficit;
        double cellHeight = layout.baseRowCellHeight(extended);
        row = VirtualFlow.createVertical(layout.getJustifiedColumnWidth(),
                                         cellHeight,
                                         FXCollections.observableArrayList(),
                                         item -> {
                                             NestedCell cell = new NestedCell(cellHeight,
                                                                              layout);
                                             cell.updateItem(item);
                                             return cell;
                                         });
        double width = layout.getJustifiedColumnWidth();
        row.setMinSize(width, rendered);
        row.setPrefSize(width, rendered);
        row.setMaxSize(width, rendered);
        getChildren().add(row);
    }

    @Override
    public void updateItem(JsonNode item) {
        row.getItems()
           .setAll(NestedTable.itemsAsArray(item));
    }
}

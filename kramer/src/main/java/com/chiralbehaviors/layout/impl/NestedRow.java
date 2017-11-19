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
import com.chiralbehaviors.layout.flowless.FlyAwayScrollPane;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * @author halhildebrand
 *
 */
public class NestedRow
        extends FlyAwayScrollPane<VirtualFlow<JsonNode, LayoutCell<NestedCell>>>
        implements LayoutCell<NestedRow> {
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
        this(layout.getJustifiedCellWidth(), cellHeight(rendered, layout),
             FXCollections.observableArrayList(), layout,
             item -> layout.extractFrom(item));
        double width = layout.getJustifiedColumnWidth();
        content.setMinSize(width, rendered);
        content.setPrefSize(width, rendered);
        content.setMaxSize(width, rendered);

    }

    protected NestedRow(double width, double cellHeight,
                        ObservableList<JsonNode> items, RelationLayout layout,
                        Function<JsonNode, JsonNode> extractor) {
        super(VirtualFlow.createVertical(layout.getJustifiedColumnWidth(),
                                         cellHeight, items, item -> {
                                             NestedCell cell = new NestedCell(cellHeight,
                                                                              layout);
                                             cell.updateItem(item);
                                             return cell;
                                         }));
        this.extractor = extractor;
        this.nestedItems = items;
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

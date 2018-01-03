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

import java.util.Arrays;

import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.style.Layout;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;

/**
 * @author halhildebrand
 *
 */
public class NestedRow extends VirtualFlow<NestedCell> {
    private static final String DEFAULT_STYLE         = "nested-row";
    private static final String SCHEMA_CLASS_TEMPLATE = "%s-nested-row";
    private static final String STYLE_SHEET           = "nested-row.css";

    public NestedRow(double rendered, RelationLayout layout,
                     int childCardinality, FocusTraversal<?> parentTraversal,
                     Layout model, RelationStyle style) {
        this(rendered, layout.getHeight(), layout, childCardinality,
             parentTraversal, model, style);
    }

    public NestedRow(double rendered, double rowHeight, RelationLayout layout,
                     int childCardinality, FocusTraversal<?> parentTraversal,
                     Layout model, RelationStyle style) {
        super(STYLE_SHEET,
              layout.getJustifiedWidth() + style.getRowCellHorizontalInset()
                           + style.getRowHorizontalInset(),
              rowHeight, FXCollections.observableArrayList(), (item, pt) -> {
                  NestedCell cell = new NestedCell(layout, pt, model);
                  cell.updateItem(item);
                  return cell;
              }, parentTraversal,
              Arrays.asList(DEFAULT_STYLE, String.format(SCHEMA_CLASS_TEMPLATE,
                                                         layout.getField())));
        double width = layout.getJustifiedWidth();
        setMinSize(width, rendered);
        setPrefSize(width, rendered);
        setMaxSize(width, rendered);
        model.apply(this, layout.getNode());
    }

    public NestedRow(String field) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
    }

    @Override
    public void dispose() {
        super.dispose();
        mouseHandler.unbind();
        if (scrollHandler != null) {
            scrollHandler.unbind();
        }
    }

    @Override
    public void updateItem(JsonNode item) {
        items.setAll(NestedTable.itemsAsArray(item));
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
    }
}

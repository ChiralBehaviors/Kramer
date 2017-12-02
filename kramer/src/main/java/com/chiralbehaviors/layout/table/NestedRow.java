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
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;

/**
 * @author halhildebrand
 *
 */
public class NestedRow extends VirtualFlow<JsonNode, NestedCell> {
    private static final String DEFAULT_STYLE         = "nested-row";
    private static final String SCHEMA_CLASS_TEMPLATE = "%s-nested-row";
    private static final String STYLE_SHEET           = "nested-row.css";

    public NestedRow(double rendered, RelationLayout layout,
                     int childCardinality) {
        super(layout.getField(), layout.getJustifiedColumnWidth(),
              layout.getHeight(), FXCollections.observableArrayList(), item -> {
                  NestedCell cell = new NestedCell(layout);
                  cell.updateItem(item);
                  return cell;
              });
        double width = layout.getJustifiedColumnWidth();
        setMinSize(width, rendered);
        setPrefSize(width, rendered);
        setMaxSize(width, rendered);
    }

    public NestedRow(String field) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
    }

    @Override
    public void dispose() {
        focus.unbind();
        mouseHandler.unbind();
        if (scrollHandler != null) {
            scrollHandler.unbind();
        }
    }

    @Override
    public void updateItem(JsonNode item) {
        items.setAll(NestedTable.itemsAsArray(item));
    }
}

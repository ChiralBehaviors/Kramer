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

package com.chiralbehaviors.layout.cell;

import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;

/**
 * @author halhildebrand
 *
 */
public class PrimitiveList extends VirtualFlow<LayoutCell<?>> {
    private static final String DEFAULT_STYLE         = "outline";
    private static final String SCHEMA_CLASS_TEMPLATE = "%s-outline";
    private static final String STYLE_SHEET           = "outline.css";

    public PrimitiveList(PrimitiveLayout layout,
                         FocusTraversal<?> parentTraversal) {
        super(layout.getField(), layout.getJustifiedWidth(),
              layout.getCellHeight(), FXCollections.observableArrayList(),
              (item, pt) -> {
                  LayoutCell<?> outlineCell = layout.buildCell(pt);
                  outlineCell.updateItem(item);
                  return outlineCell;
              }, parentTraversal);
        double width = layout.getJustifiedColumnWidth();
        double height = layout.getHeight();
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
    }

    public PrimitiveList(String field) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
    }

    @Override
    public void dispose() {
        mouseHandler.unbind();
        if (scrollHandler != null) {
            scrollHandler.unbind();
        }
    }

    @Override
    public void updateItem(JsonNode item) {
        items.setAll(SchemaNode.asList(item));
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
    }
}

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

package com.chiralbehaviors.layout.primitives;

import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.cell.HorizontalCell;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author halhildebrand
 *
 */
public class PrimitiveCell extends HorizontalCell<PrimitiveCell> {

    private static final String DEFAULT_STYLE         = "primitive-cell";
    private static final String SCHEMA_CLASS_TEMPLATE = "%s-primitive-cell";
    private static final String STYLE_SHEET           = "primitive-cell.css";

    private LayoutCell<?>       primitive;

    public PrimitiveCell(PrimitiveLayout layout) {
        this(layout.getField());
        primitive = layout.buildCell();
        getChildren().add(primitive.getNode());
        setMinSize(layout.getJustifiedColumnWidth(), layout.getCellHeight());
        setPrefSize(layout.getJustifiedColumnWidth(), layout.getCellHeight());
        setMaxSize(layout.getJustifiedColumnWidth(), layout.getCellHeight());
    }

    public PrimitiveCell(String field) {
        super(STYLE_SHEET);
        getStyleClass().addAll(DEFAULT_STYLE,
                               String.format(SCHEMA_CLASS_TEMPLATE, field));
    }

    public void setFocus(boolean focussed) {
        super.setFocused(focussed);
    }

    @Override
    public void updateItem(JsonNode item) {
        primitive.updateItem(item);
    }

}

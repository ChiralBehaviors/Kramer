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

import java.util.ArrayList;
import java.util.List;

import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.cell.VerticalCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.Layout;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Pos;
import javafx.scene.layout.Region;

/**
 * @author halhildebrand
 *
 */
public class NestedTable extends VerticalCell<NestedTable> {
    private static final String DEFAULT_STYLE         = "nested-table";
    private static final String SCHEMA_CLASS_TEMPLATE = "%s-nested-table";
    private static final String STYLE_SHEET           = "nested-table.css";

    public static List<JsonNode> itemsAsArray(JsonNode items) {
        List<JsonNode> itemArray = new ArrayList<>();
        SchemaNode.asArray(items)
                  .forEach(n -> itemArray.add(n));
        return itemArray;
    }

    private final VirtualFlow<NestedCell> rows;

    public NestedTable(int childCardinality, RelationLayout layout,
                       FocusTraversal<?> parentTraversal, Layout model,
                       RelationStyle style) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        setAlignment(Pos.CENTER);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE,
                                          layout.getField()));
        Region header = layout.buildColumnHeader();
        rows = new NestedRow(Layout.snap(layout.getHeight()
                                         - layout.getColumnHeaderHeight()),
                             layout.getRowCellHeight(), layout,
                             childCardinality, parentTraversal, model, style);

        getChildren().addAll(header, rows);
        model.apply(rows, layout.getNode());
    }

    public NestedTable(String field) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
        this.rows = null;
    }

    @Override
    public void activate() {
        rows.activate();
    }

    @Override
    public void setFocus() {
        rows.setFocus();
    }

    @Override
    public void updateItem(JsonNode item) {
        rows.getItems()
            .setAll(SchemaNode.asList(item));
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
    }
}

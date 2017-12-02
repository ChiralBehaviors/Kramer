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

import static com.chiralbehaviors.layout.LayoutProvider.snap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.cell.FocusTraversal;
import com.chiralbehaviors.layout.cell.MouseHandler;
import com.chiralbehaviors.layout.cell.VerticalCell;
import com.chiralbehaviors.layout.flowless.ScrollHandler;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.flowless.VirtualFlowHit;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
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

    private final FocusTraversal                    focus;
    private final MouseHandler                      mouseHandler;
    private final VirtualFlow<JsonNode, NestedCell> rows;
    private ScrollHandler                           scrollHandler;

    {
        focus = new FocusTraversal() {

            @Override
            protected Node getNode() {
                return NestedTable.this;
            }

        };

    }

    public NestedTable(int childCardinality, RelationLayout layout) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE,
                                          layout.getField()));
        Region header = layout.buildColumnHeader();
        double width = layout.getJustifiedColumnWidth();
        double height = snap(layout.getHeight()
                             - layout.getColumnHeaderHeight());

        rows = buildRows(width, height, childCardinality, layout);
        getChildren().addAll(header, rows);
        setMinWidth(layout.getJustifiedColumnWidth());
        setPrefWidth(layout.getJustifiedColumnWidth());
        setMaxWidth(layout.getJustifiedColumnWidth());
        mouseHandler = new MouseHandler() {

            @Override
            public Node getNode() {
                return NestedTable.this;
            }

            public void select(MouseEvent evt) {
                VirtualFlowHit<NestedCell> hit = rows.hit(evt.getX(),
                                                          evt.getY() - layout.getColumnHeaderHeight());
                if (hit.isCellHit()) {
                    NestedCell node = hit.getCell()
                                         .getNode();
                    node.setFocus(true);
                }
            }
        };
        scrollHandler = new ScrollHandler(rows);
    }

    public NestedTable(String field) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
        this.rows = null;
        mouseHandler = null;
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
        rows.getItems()
            .setAll(SchemaNode.asList(item));
    }

    protected VirtualFlow<JsonNode, NestedCell> buildRows(double width,
                                                          double height,
                                                          int childCardinality,
                                                          RelationLayout layout) {
        Function<JsonNode, NestedCell> factory = item -> {
            NestedCell cell = new NestedCell(layout);
            cell.updateItem(item);
            return cell;
        };
        VirtualFlow<JsonNode, NestedCell> rows = new VirtualFlow<JsonNode, NestedCell>(DEFAULT_STYLE,
                                                                                       layout.getJustifiedColumnWidth(),
                                                                                       layout.getRowHeight(),
                                                                                       FXCollections.observableArrayList(),
                                                                                       factory);
        rows.setMinSize(width, height);
        rows.setPrefSize(width, height);
        rows.setMaxSize(width, height);
        return rows;
    }
}

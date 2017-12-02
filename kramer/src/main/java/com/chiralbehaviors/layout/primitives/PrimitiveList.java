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

import java.util.function.Function;

import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.cell.FocusTraversal;
import com.chiralbehaviors.layout.cell.HorizontalCell;
import com.chiralbehaviors.layout.cell.MouseHandler;
import com.chiralbehaviors.layout.flowless.ScrollHandler;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.flowless.VirtualFlowHit;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

/**
 * @author halhildebrand
 *
 */
public class PrimitiveList extends HorizontalCell<PrimitiveList> {
    private static final String                  DEFAULT_STYLE         = "outline";
    private static final String                  SCHEMA_CLASS_TEMPLATE = "%s-outline";
    private static final String                  STYLE_SHEET           = "outline.css";

    private final FocusTraversal                 focus;
    private final ObservableList<JsonNode>       items                 = FXCollections.observableArrayList();
    private final MouseHandler                   mouseHandler;
    private ScrollHandler                        scrollHandler;
    private VirtualFlow<JsonNode, PrimitiveCell> list;

    {
        focus = new FocusTraversal() {

            @Override
            protected Node getNode() {
                return PrimitiveList.this;
            }
        };
        mouseHandler = new MouseHandler() {

            @Override
            public Node getNode() {
                return PrimitiveList.this;
            }

            public void select(MouseEvent evt) {
                VirtualFlowHit<PrimitiveCell> hit = list.hit(evt.getX(),
                                                             evt.getY());
                if (hit.isCellHit()) {
                    hit.getCell()
                       .setFocus(true);
                }
            }
        };
    }

    public PrimitiveList(PrimitiveLayout layout) {
        this(layout.getField());
        Function<JsonNode, PrimitiveCell> cell = item -> {
            PrimitiveCell outlineCell = new PrimitiveCell(layout);
            outlineCell.updateItem(item);
            return outlineCell;
        };
        list = VirtualFlow.createVertical(layout.getJustifiedWidth(),
                                          layout.getCellHeight(), items,
                                          jsonNode -> cell.apply(jsonNode));
        list.setMinSize(layout.getJustifiedColumnWidth(), layout.getHeight());
        list.setPrefSize(layout.getJustifiedColumnWidth(), layout.getHeight());
        list.setMaxSize(layout.getJustifiedColumnWidth(), layout.getHeight());

        getChildren().add(list);
        scrollHandler = new ScrollHandler(list);
    }

    public PrimitiveList(String field) {
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
        items.setAll(SchemaNode.asList(item));
    }
}

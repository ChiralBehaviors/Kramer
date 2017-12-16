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

package com.chiralbehaviors.layout.outline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.chiralbehaviors.layout.ColumnSet;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.cell.FocusTraversal;
import com.chiralbehaviors.layout.cell.FocusTraversal.Bias;
import com.chiralbehaviors.layout.cell.VerticalCell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * @author halhildebrand
 *
 */
public class OutlineCell extends VerticalCell<OutlineCell> {

    private static final String  DEFAULT_STYLE         = "outline-cell";
    private static final String  SCHEMA_CLASS_TEMPLATE = "%s-outline-cell";
    private static final String  STYLE_SHEET           = "outline-cell.css";

    private final FocusTraversal focus;
    private int                  selected              = -1;
    private List<Span>           spans                 = new ArrayList<>();

    public OutlineCell(Collection<ColumnSet> columnSets, int childCardinality,
                       double cellHeight, RelationLayout layout,
                       FocusTraversal parentTraversal) {
        this(layout.getField(), parentTraversal);
        setMinSize(layout.getJustifiedColumnWidth(), cellHeight);
        setPrefSize(layout.getJustifiedColumnWidth(), cellHeight);
        setMaxSize(layout.getJustifiedColumnWidth(), cellHeight);
        columnSets.forEach(cs -> {
            Span span = new Span(layout.getField(), cs.getWidth(),
                                 cs.getColumns(), childCardinality,
                                 cs.getCellHeight(), layout.getLabelWidth(),
                                 focus);
            spans.add(span);
            VBox.setVgrow(span.getNode(), Priority.ALWAYS);
            getChildren().add(span.getNode());
        });
    }

    public OutlineCell(String field) {
        this(field, null);
    }

    public OutlineCell(String field, FocusTraversal parent) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
        focus = new FocusTraversal(parent, Bias.VERTICAL) {

            @Override
            public void selectNext() {
                selected = selected + 1;
                if (selected == spans.size()) {
                    selected = selected - 1;
                    traverseNext();
                } else {
                    spans.get(selected)
                         .setFocus();
                }
            }

            @Override
            public void selectPrevious() {
                selected = selected - 1;
                if (selected < 0) {
                    selected = -1;
                    traversePrevious();
                } else {
                    spans.get(selected)
                         .setFocus();
                }
            }

            @Override
            protected Node getNode() {
                return OutlineCell.this;
            }
        };

    }

    @Override
    public void dispose() {
        focus.unbind();
    }

    @Override
    public void updateIndex(int index) {
        boolean active = ((index % 2) == 0);
        pseudoClassStateChanged(PSEUDO_CLASS_EVEN, active);
        pseudoClassStateChanged(PSEUDO_CLASS_ODD, !active);
    }

    @Override
    public void updateItem(JsonNode item) {
        spans.forEach(s -> s.updateItem(item));
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
    }
}

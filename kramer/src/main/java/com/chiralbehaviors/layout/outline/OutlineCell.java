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
import com.chiralbehaviors.layout.cell.Hit;
import com.chiralbehaviors.layout.cell.LayoutContainer;
import com.chiralbehaviors.layout.cell.VerticalCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.cell.control.FocusTraversalNode;
import com.chiralbehaviors.layout.cell.control.FocusTraversalNode.Bias;
import com.chiralbehaviors.layout.cell.control.MouseHandler;
import com.chiralbehaviors.layout.cell.control.MultipleCellSelection;
import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.Node;

/**
 * @author halhildebrand
 *
 */
public class OutlineCell extends VerticalCell<OutlineCell>
        implements LayoutContainer<JsonNode, OutlineCell, Span> {

    private static final String                         OUTLINE_CELL_CLASS    = "outline-cell";
    private static final String                         SCHEMA_CLASS_TEMPLATE = "%s-outline-cell";
    private static final String                         STYLE_SHEET           = "outline-cell.css";

    private final FocusTraversal<Span>                  focus;
    private final MouseHandler                          mouseHandler;
    private final MultipleCellSelection<JsonNode, Span> selectionModel;
    private List<Span>                                  spans                 = new ArrayList<>();

    public OutlineCell(Collection<ColumnSet> columnSets, int childCardinality,
                       RelationLayout layout, FocusTraversal<OutlineCell> pt,
                       Style model, RelationStyle style, double labelWidth) {
        this(layout.getField(), pt);
        columnSets.forEach(cs -> {
            Span span = new Span(childCardinality, layout, labelWidth, cs,
                                 focus, model, style);
            spans.add(span);
            alignmentProperty();
            getChildren().add(span.getNode());
        });
    }

    public OutlineCell(String field) {
        this(field, null);
    }

    public OutlineCell(String field, FocusTraversal<OutlineCell> parent) {
        super(STYLE_SHEET);
        initialize(OUTLINE_CELL_CLASS);
        getStyleClass().addAll(String.format(SCHEMA_CLASS_TEMPLATE, field));
        selectionModel = buildSelectionModel(i -> null, () -> spans.size(),
                                             i -> spans.get(i));
        mouseHandler = bind(selectionModel);
        focus = new FocusTraversalNode<Span>(parent, selectionModel,
                                             Bias.VERTICAL) {

            @Override
            protected Node getNode() {
                return OutlineCell.this;
            }
        };

    }

    @Override
    public void activate() {
        focus.setCurrent();
    }

    @Override
    public void dispose() {
        super.dispose();
        mouseHandler.unbind();
    }

    @Override
    public Hit<Span> hit(double x, double y) {
        return hit(x, y, spans);
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

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
import java.util.List;

import com.chiralbehaviors.layout.ColumnSet;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.cell.Hit;
import com.chiralbehaviors.layout.cell.HorizontalCell;
import com.chiralbehaviors.layout.cell.LayoutContainer;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.cell.control.FocusTraversalNode;
import com.chiralbehaviors.layout.cell.control.FocusTraversalNode.Bias;
import com.chiralbehaviors.layout.cell.control.MouseHandler;
import com.chiralbehaviors.layout.cell.control.MultipleCellSelection;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author halhildebrand
 *
 */
public class Span extends HorizontalCell<Span>
        implements LayoutContainer<JsonNode, Span, OutlineColumn> {

    private static final String                                  DEFAULT_STYLE = "span";
    private static final String                                  S_SPAN        = "%s-span";
    private static final String                                  STYLE_SHEET   = "span.css";
    private final List<OutlineColumn>                            columns       = new ArrayList<>();
    private final FocusTraversal<OutlineColumn>                  focus;
    private int                                                  index;
    private final MouseHandler                                   mouseModel;
    private final MultipleCellSelection<JsonNode, OutlineColumn> selectionModel;

    public Span(int cardinality, RelationLayout layout, double labelWidth,
                ColumnSet columnSet, FocusTraversal<Span> parentTraversal,
                Style model, RelationStyle style) {
        this(layout.getField(), parentTraversal);

        columnSet.getColumns()
                 .forEach(c -> {
                     OutlineColumn cell = new OutlineColumn(layout.getField(),
                                                            c, cardinality,
                                                            labelWidth, focus,
                                                            model, style);
                     this.columns.add(cell);
                     getChildren().add(cell.getNode());
                 });
    }

    public Span(String field) {
        this(field, null);
    }

    public Span(String field, FocusTraversal<Span> parentTraversal) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(S_SPAN, field));
        selectionModel = buildSelectionModel(i -> null, () -> columns.size(),
                                             i -> columns.get(i));
        focus = new FocusTraversalNode<OutlineColumn>(parentTraversal,
                                                      selectionModel,
                                                      Bias.HORIZONTAL) {

            @Override
            protected Span getContainer() {
                return Span.this;
            }

        };
        mouseModel = bind(selectionModel);
    }

    @Override
    public void activate() {
        focus.setCurrent();
    }

    @Override
    public void dispose() {
        super.dispose();
        mouseModel.unbind();
        focus.unbind();
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public Hit<OutlineColumn> hit(double x, double y) {
        return hit(x, y, columns);
    }

    @Override
    public void updateIndex(int index) {
        this.index = index;
    }

    @Override
    public void updateItem(JsonNode item) {
        columns.forEach(c -> c.updateItem(item));
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
    }
}

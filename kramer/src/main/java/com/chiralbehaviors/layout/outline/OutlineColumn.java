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
import java.util.function.Consumer;

import com.chiralbehaviors.layout.Column;
import com.chiralbehaviors.layout.cell.Hit;
import com.chiralbehaviors.layout.cell.LayoutContainer;
import com.chiralbehaviors.layout.cell.VerticalCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.cell.control.FocusTraversalNode;
import com.chiralbehaviors.layout.cell.control.FocusTraversalNode.Bias;
import com.chiralbehaviors.layout.cell.control.MouseHandler;
import com.chiralbehaviors.layout.cell.control.MultipleCellSelection;
import com.chiralbehaviors.layout.style.Layout;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Pos;
import javafx.scene.Node;

/**
 * @author halhildebrand
 *
 */
public class OutlineColumn extends VerticalCell<OutlineColumn>
        implements LayoutContainer<JsonNode, OutlineColumn, OutlineElement> {

    private static final String                                   DEFAULT_STYLE         = "outline-column";
    private static final String                                   SCHEMA_CLASS_TEMPLATE = "%s-outline-column";
    private static final String                                   STYLE_SHEET           = "outline-column.css";
    private final List<OutlineElement>                            elements              = new ArrayList<>();
    private final List<Consumer<JsonNode>>                        fields                = new ArrayList<>();
    private final FocusTraversal<OutlineElement>                  focus;
    private final MouseHandler                                    mouseHandler;
    private final MultipleCellSelection<JsonNode, OutlineElement> selectionModel;

    public OutlineColumn(String field) {
        this(field, null);
    }

    public OutlineColumn(String field, Column c, int cardinality,
                         double labelWidth, double height,
                         FocusTraversal<OutlineColumn> parentTraversal,
                         Layout model, RelationStyle style) {
        this(field, parentTraversal);
        setAlignment(Pos.CENTER);
        double width = c.getWidth() + style.getColumnHorizontalInset();
        double expanded = height + style.getColumnVerticalInset();
        setMinSize(width, expanded);
        setMaxSize(width, expanded);
        setPrefSize(width, expanded);
        c.getFields()
         .forEach(f -> {
             OutlineElement cell = new OutlineElement(field, f, cardinality,
                                                      labelWidth, c.getWidth(),
                                                      focus, model, style);
             elements.add(cell);
             fields.add(item -> cell.updateItem(f.extractFrom(item)));
             getChildren().add(cell.getNode());
         });
    }

    public OutlineColumn(String field,
                         FocusTraversal<OutlineColumn> parentTraversal) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        setAlignment(Pos.CENTER);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
        selectionModel = buildSelectionModel(i -> null, () -> elements.size(),
                                             i -> elements.get(i));
        mouseHandler = bind(selectionModel);
        focus = new FocusTraversalNode<OutlineElement>(parentTraversal,
                                                       selectionModel,
                                                       Bias.VERTICAL) {

            @Override
            protected Node getNode() {
                return OutlineColumn.this;
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
    public Hit<OutlineElement> hit(double x, double y) {
        return hit(x, y, elements);
    }

    @Override
    public void updateItem(JsonNode item) {
        fields.forEach(m -> m.accept(item));
    }
}

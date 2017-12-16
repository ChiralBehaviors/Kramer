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
import com.chiralbehaviors.layout.cell.FocusTraversal;
import com.chiralbehaviors.layout.cell.FocusTraversal.Bias;
import com.chiralbehaviors.layout.cell.MultipleCellSelection;
import com.chiralbehaviors.layout.cell.VerticalCell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.Node;

/**
 * @author halhildebrand
 *
 */
public class OutlineColumn extends VerticalCell<OutlineColumn> {

    private static final String                                   DEFAULT_STYLE         = "span";
    private static final String                                   SCHEMA_CLASS_TEMPLATE = "%s-outline-column";
    private static final String                                   STYLE_SHEET           = "outline-column.css";
    private final List<OutlineElement>                            elements              = new ArrayList<>();
    private final List<Consumer<JsonNode>>                        fields                = new ArrayList<>();
    private final FocusTraversal<OutlineElement>                  focus;
    private final MultipleCellSelection<JsonNode, OutlineElement> selectionModel;

    public OutlineColumn(String field) {
        this(field, null);
    }

    public OutlineColumn(String field, Column c, int cardinality,
                         double labelWidth, double cellHeight,
                         FocusTraversal<OutlineColumn> focus) {
        this(field, focus);
        setMinSize(c.getWidth(), cellHeight);
        setMaxSize(c.getWidth(), cellHeight);
        setPrefSize(c.getWidth(), cellHeight);
        c.getFields()
         .forEach(f -> {
             OutlineElement cell = f.outlineElement(field, cardinality,
                                                    labelWidth, c.getWidth(),
                                                    null);
             elements.add(cell);
             fields.add(item -> cell.updateItem(f.extractFrom(item)));
             getChildren().add(cell.getNode());
         });
    }

    public OutlineColumn(String field,
                         FocusTraversal<OutlineColumn> parentTraversal) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
        selectionModel = new MultipleCellSelection<JsonNode, OutlineElement>() {

            @Override
            public OutlineElement getCell(int index) {
                return elements.get(index);
            }

            @Override
            public int getItemCount() {
                return elements.size();
            }

            @Override
            public JsonNode getModelItem(int index) {
                return null;
            }
        };
        focus = new FocusTraversal<OutlineElement>(parentTraversal,
                                                   selectionModel,
                                                   Bias.VERTICAL) {

            @Override
            protected Node getNode() {
                return OutlineColumn.this;
            }
        };
    }

    @Override
    public void dispose() {
        focus.unbind();
    }

    @Override
    public void updateItem(JsonNode item) {
        fields.forEach(m -> m.accept(item));
    }
}

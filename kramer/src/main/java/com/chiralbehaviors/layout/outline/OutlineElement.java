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

import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.cell.HorizontalCell;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.flowless.Cell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Control;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * @author halhildebrand
 *
 */
public class OutlineElement extends HorizontalCell<OutlineElement> {

    private static final String                    DEFAULT_STYLE = "outline-element";
    private static final String                    STYLE_SHEET   = "outline-element.css";

    private final Cell<JsonNode, ? extends Region> cell;

    public OutlineElement(LayoutCell<? extends Region> cell) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        this.cell = cell;
    }

    public OutlineElement(Control label, LayoutCell<? extends Region> cell,
                          int cardinality, double labelWidth, double justified,
                          double height) {
        this(cell);

        setMinSize(justified, height);
        setPrefSize(justified, height);
        setMaxSize(justified, height);
        VBox.setVgrow(this, Priority.ALWAYS);

        label.setMinWidth(labelWidth);
        label.setMaxWidth(labelWidth);
        double available = justified - labelWidth;
        cell.getNode()
            .setMinSize(available, height);
        cell.getNode()
            .setPrefSize(available, height);
        cell.getNode()
            .setMaxSize(available, height);
        getChildren().addAll(label, cell.getNode());

    }

    public OutlineElement(PrimitiveLayout p, int cardinality, double labelWidth,
                          double justified) {
        this(p.label(labelWidth), p.buildControl(), cardinality, labelWidth,
             justified, p.getHeight());
    }

    public OutlineElement(RelationLayout layout, int cardinality,
                          double labelWidth, double justified) {
        this(layout.label(labelWidth), layout.buildControl(), cardinality,
             labelWidth, justified, layout.getHeight());
    }

    @Override
    public void updateItem(JsonNode item) {
        cell.updateItem(item);
    }
}

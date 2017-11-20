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

import java.util.function.Function;

import com.chiralbehaviors.layout.LayoutCell;
import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.flowless.Cell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.Control;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * @author halhildebrand
 *
 */
public class OutlineElement extends HBox implements LayoutCell<OutlineElement> {

    private static final String                    DEFAULT_STYLE = "outline-element";

    private final Cell<JsonNode, ? extends Region> cell;
    private final Function<JsonNode, JsonNode>     extractor;

    public OutlineElement(Control label, LayoutCell<? extends Region> cell,
                          int cardinality, double labelWidth,
                          Function<JsonNode, JsonNode> extractor,
                          double justified, String field, double height) {
        setDefaultStyles(DEFAULT_STYLE);

        this.extractor = extractor.andThen(n -> {
            return n.get(field);
        });

        setMinSize(justified, height);
        setPrefSize(justified, height);
        setMaxSize(justified, height);
        VBox.setVgrow(this, Priority.ALWAYS);

        label.setMinWidth(labelWidth);
        label.setMaxWidth(labelWidth);

        this.cell = cell;
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
                          Function<JsonNode, JsonNode> extractor,
                          double justified) {
        this(p.label(labelWidth), p.buildControl(1), cardinality, labelWidth,
             extractor, justified, p.getField(), p.getHeight());
    }

    public OutlineElement(RelationLayout layout, int cardinality,
                          double labelWidth,
                          Function<JsonNode, JsonNode> extractor,
                          double justified) {
        this(layout.label(labelWidth), layout.buildControl(1, extractor),
             cardinality, labelWidth, extractor, justified, layout.getField(),
             layout.getHeight());
    }

    @Override
    public void updateItem(JsonNode item) {
        cell.updateItem(extractor.apply(item));
    }
}

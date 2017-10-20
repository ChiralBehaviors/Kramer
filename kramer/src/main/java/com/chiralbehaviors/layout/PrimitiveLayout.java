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

package com.chiralbehaviors.layout;

import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.control.JsonControl;
import com.chiralbehaviors.layout.control.PrimitiveControl;
import com.chiralbehaviors.layout.schema.Primitive;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 *
 * @author halhildebrand
 *
 */
public class PrimitiveLayout extends SchemaNodeLayout {
    private final Layout    layout;
    private final Primitive p;

    public PrimitiveLayout(Layout layout, Primitive p) {
        this.layout = layout;
        this.p = p;
    }

    @Override
    public double baseOutlineWidth(double available) {
        return layout.baseTextWidth(available);
    }

    @Override
    public double baseTableColumnWidth(double width) {
        return layout.baseTextWidth(width);
    }

    public PrimitiveControl buildControl(int cardinality) {
        return new PrimitiveControl(p);
    }

    public Double cellHeight(double maxWidth, double justified) {
        double rows = Math.ceil((maxWidth / justified) + 0.5);
        return (layout.getTextLineHeight() * rows)
               + layout.getTextVerticalInset();
    }

    @Override
    public Control label(double labelWidth, String label, double height) {
        return layout.label(labelWidth, label, height);
    }

    @Override
    public double labelWidth(String label) {
        return layout.labelWidth(label);
    }

    public Pair<Consumer<JsonNode>, Parent> outlineElement(String field,
                                                           int cardinality,
                                                           Double height,
                                                           String label,
                                                           double labelWidth,
                                                           Function<JsonNode, JsonNode> extractor,
                                                           double justified) {
        HBox box = new HBox();
        box.setPrefWidth(justified);
        box.setPrefHeight(height);
        VBox.setVgrow(box, Priority.ALWAYS);

        Control labelControl = label(labelWidth, label, height);
        JsonControl control = buildControl(cardinality);
        control.setPrefHeight(height);
        control.setPrefWidth(justified);

        box.getChildren()
           .add(labelControl);
        box.getChildren()
           .add(control);

        return new Pair<>(item -> {
            control.setItem(extractor.apply(item)
                                     .get(field));
        }, box);
    }

    @Override
    public double tableColumnWidth(double width) {
        return layout.totalTextWidth(width);
    }

    public double width(JsonNode row) {
        return layout.textWidth(Layout.toString(row));
    }
}
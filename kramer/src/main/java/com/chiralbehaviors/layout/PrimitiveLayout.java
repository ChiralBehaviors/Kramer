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

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.control.JsonControl;
import com.chiralbehaviors.layout.control.PrimitiveControl;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 *
 * @author halhildebrand
 *
 */
public class PrimitiveLayout extends SchemaNodeLayout {
    private double          columnWidth;
    private double          maxWidth;
    private final Primitive p;
    private boolean         variableLength;

    public PrimitiveLayout(LayoutProvider layout, Primitive p) {
        super(layout);
        this.p = p;
    }

    @Override
    public double baseTableColumnWidth(double width) {
        return layout.baseTextWidth(width);
    }

    public PrimitiveControl buildControl(int cardinality) {
        return new PrimitiveControl(p);
    }

    public double cellHeight(double justified) {
        if (height > 0) {
            return height;
        }
        double rows = Math.ceil((maxWidth / justified) + 0.5);
        height = (layout.getTextLineHeight() * rows)
                 + layout.getTextVerticalInset();
        return height;
    }

    public Function<Double, Region> columnHeader() {
        return rendered -> layout.label(layout.totalTextWidth(justifiedWidth)
                                        + indentation(indent), p.getLabel(),
                                        rendered);
    }

    public void compress(double available) {
        justifiedWidth = baseOutlineWidth(available);
    }

    public JsonNode extractFrom(JsonNode node) {
        return p.extractFrom(node);
    }

    public double justify(double available) {
        justifiedWidth = baseTableColumnWidth(available);
        return justifiedWidth;
    }

    @Override
    public double labelWidth(String label) {
        return layout.labelWidth(label);
    }

    @Override
    public double layout(int cardinality, double width) {
        clear();
        return variableLength ? width : Math.min(width, columnWidth);
    }

    @Override
    public double measure(JsonNode data, boolean singular, INDENT indentation) {
        clear();
        indent = indentation;
        double labelWidth = labelWidth(p.getLabel());
        double sum = 0;
        maxWidth = 0;
        columnWidth = 0;
        for (JsonNode prim : SchemaNode.asList(data)) {
            List<JsonNode> rows = SchemaNode.asList(prim);
            double width = 0;
            for (JsonNode row : rows) {
                width += width(row);
                maxWidth = Math.max(maxWidth, width);
            }
            sum += rows.isEmpty() ? 1 : width / rows.size();
        }
        double averageWidth = data.size() == 0 ? 0 : (sum / data.size());

        columnWidth = LayoutProvider.snap(Math.max(labelWidth,
                                                   Math.max(p.getDefaultWidth(),
                                                            averageWidth)));
        if (maxWidth > averageWidth) {
            variableLength = true;
        }

        return tableColumnWidth(columnWidth);

    }

    public Pair<Consumer<JsonNode>, Parent> outlineElement(String field,
                                                           int cardinality,
                                                           String label,
                                                           double labelWidth,
                                                           Function<JsonNode, JsonNode> extractor,
                                                           double justified) {
        HBox box = new HBox();
        box.setPrefSize(justified, height);
        VBox.setVgrow(box, Priority.ALWAYS);

        Control labelControl = label(labelWidth, label);
        JsonControl control = buildControl(cardinality);
        control.setPrefSize(justified, height);

        box.getChildren()
           .add(labelControl);
        box.getChildren()
           .add(control);

        return new Pair<>(item -> {
            control.setItem(extractor.apply(item)
                                     .get(field));
        }, box);
    }

    public double tableColumnWidth() {
        return tableColumnWidth(columnWidth);
    }

    @Override
    public double tableColumnWidth(double width) {
        return layout.totalTextWidth(width);
    }

    public double width(JsonNode row) {
        return layout.textWidth(LayoutProvider.toString(row));
    }

    @Override
    protected double baseOutlineWidth(double available) {
        return layout.baseTextWidth(available);
    }
}
/**
 * Copyright (c) 2016 Chiral Behaviors, LLC, all rights reserved.
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

package com.chiralbehaviors.layout.schema;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.Layout;
import com.chiralbehaviors.layout.NestedTable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 * @author hhildebrand
 *
 */
public class Primitive extends SchemaNode {

    private double  columnWidth       = 0;
    private double  maxWidth          = 0;
    private double  valueDefaultWidth = 0;
    private boolean variableLength    = false;

    public Primitive() {
        super();
    }

    public Primitive(String label) {
        super(label);
    }

    @Override
    public Pair<Consumer<JsonNode>, Region> buildColumn(NestedTable table,
                                                        double rendered,
                                                        Layout layout) {
        return table.buildPrimitive(rendered, this, layout);
    }

    @Override
    public String toString() {
        return String.format("Primitive [%s:%.2f:%.2f]", label, columnWidth,
                             justifiedWidth);
    }

    @Override
    public String toString(int indent) {
        return toString();
    }

    @Override
    double cellHeight(int cardinality, Layout layout, double justified) {
        if (height != null) {
            return height;
        }
        double rows = Math.ceil((maxWidth / justified) + 0.5);
        height = layout.textHeight(rows);
        return height;
    }

    @Override
    void compress(Layout layout, double available) {
        justifiedWidth = layout.baseTextWidth(available);
    }

    @Override
    void justify(double width, Layout layout) {
        justifiedWidth = layout.baseTextWidth(width);
    }

    @Override
    double layout(int cardinality, Layout layout, double width) {
        height = null;
        return variableLength ? width : Math.min(width, columnWidth);
    }

    /* (non-Javadoc)
     * @see com.chiralbehaviors.layout.schema.SchemaNode#layoutWidth(com.chiralbehaviors.layout.Layout)
     */
    @Override
    double layoutWidth(Layout layout) {
        return tableColumnWidth(layout);
    }

    @Override
    double measure(Relation parent, JsonNode data, boolean singular,
                   Layout layout) {
        double labelWidth = getLabelWidth(layout);
        double sum = 0;
        maxWidth = 0;
        columnWidth = 0;
        justifiedWidth = null;
        for (JsonNode prim : SchemaNode.asList(data)) {
            List<JsonNode> rows = SchemaNode.asList(prim);
            double width = 0;
            for (JsonNode row : rows) {
                width += layout.textWidth(toString(row));
                maxWidth = Math.max(maxWidth, width);
            }
            sum += rows.isEmpty() ? 1 : width / rows.size();
        }
        double averageWidth = data.size() == 0 ? 0 : (sum / data.size());

        columnWidth = Layout.snap(Math.max(labelWidth,
                                           Math.max(valueDefaultWidth,
                                                    averageWidth)));
        if (maxWidth > averageWidth) {
            variableLength = true;
        }

        return layout.totalTextWidth(columnWidth);
    }

    @Override
    Pair<Consumer<JsonNode>, Parent> outlineElement(int cardinality,
                                                    double labelWidth,
                                                    Function<JsonNode, JsonNode> extractor,
                                                    Layout layout,
                                                    double justified) {
        HBox box = new HBox();
        box.setPrefWidth(justified);
        box.setPrefHeight(height);
        VBox.setVgrow(box, Priority.ALWAYS);

        Label labelText = label(labelWidth);
        Control control = buildControl(cardinality, layout);
        control.setPrefHeight(height);
        control.setPrefWidth(justified);

        box.getChildren()
           .add(labelText);
        box.getChildren()
           .add(control);

        return new Pair<>(item -> {
            JsonNode extracted = extractor.apply(item);
            JsonNode extractedField = extracted.get(field);
            setItems(control, extractedField, layout);
        }, box);
    }

    @Override
    double outlineWidth(Layout layout) {
        return tableColumnWidth(layout);
    }

    @Override
    double rowHeight(int cardinality, Layout layout, double width) {
        return cellHeight(cardinality, layout, width);
    }

    @Override
    double tableColumnWidth(Layout layout) {
        return layout.totalTextWidth(columnWidth);
    }

    private Label buildControl(int cardinality, Layout layout) {
        Label text = new Label();
        text.setWrapText(true);
        text.setStyle("-fx-background-color: " + "         rgba(0,0,0,0.08),"
                      + "        linear-gradient(#9a9a9a, #909090),"
                      + "        white 0%;"
                      + "    -fx-background-insets: 0 0 -1 0,0,1;"
                      + "    -fx-background-radius: 5,5,4;"
                      + "    -fx-padding: 3 30 3 30;"
                      + "    -fx-text-fill: #242d35;"
                      + "    -fx-font-size: 14px;");
        return text;
    }

    private String toString(JsonNode value) {
        if (value == null) {
            return "";
        }
        if (value instanceof ArrayNode) {
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (JsonNode e : value) {
                if (first) {
                    first = false;
                    builder.append('[');
                } else {
                    builder.append(", ");
                }
                builder.append(e.asText());
            }
            builder.append(']');
            return builder.toString();
        } else {
            return value.asText();
        }
    }
}

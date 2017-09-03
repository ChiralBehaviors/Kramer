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
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.Layout;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
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
    public String toString() {
        return String.format("Primitive [%s:%.2f:%.2f]", label, columnWidth,
                             justifiedWidth);
    }

    @Override
    public String toString(int indent) {
        return toString();
    }

    @Override
    Function<Double, Pair<Consumer<JsonNode>, Control>> buildColumn(int cardinality,
                                                                    Function<JsonNode, JsonNode> extractor,
                                                                    Map<SchemaNode, TableColumn<JsonNode, ?>> columnMap,
                                                                    Layout layout,
                                                                    double inset,
                                                                    INDENT indent) {
        return resolvedHeight -> {
            TextArea control = buildControl(1, layout);
            control.setPrefHeight(resolvedHeight);
            bind(control, columnMap.get(this), inset);
            layout.getModel()
                  .apply(control, Primitive.this);
            return new Pair<>(node -> setItems(control, extractFrom(node),
                                               layout),
                              control);
        };
    }

    @Override
    TableColumn<JsonNode, JsonNode> buildColumn(Layout layout, double inset,
                                                INDENT indent) {
        TableColumn<JsonNode, JsonNode> column = super.buildColumn(layout,
                                                                   inset,
                                                                   indent);
        column.setPrefWidth(justifiedWidth + inset);
        return column;
    }

    @Override
    double cellHeight(int cardinality, Layout layout, double justified) {
        if (height != null) {
            return height;
        }
        double rows = Math.ceil(maxWidth / justified) + 1;
        height = Layout.snap(layout.getTextLineHeight() * rows)
                 + layout.getTextVerticalInset();
        return height;
    }

    @Override
    void justify(double width, Layout layout) {
        justifiedWidth = Layout.snap(width);
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
    double measure(JsonNode data, boolean singular, Layout layout,
                   INDENT indent) {
        double labelWidth = getLabelWidth(layout);
        double sum = 0;
        maxWidth = 0;
        columnWidth = 0;
        for (JsonNode prim : data) {
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
        columnWidth += indent == INDENT.RIGHT ? 26 : 0;
        if (maxWidth > averageWidth) {
            variableLength = true;
        }

        return columnWidth + layout.getTextHorizontalInset();
    }

    @Override
    Pair<Consumer<JsonNode>, Parent> outlineElement(int cardinality,
                                                    double labelWidth,
                                                    Function<JsonNode, JsonNode> extractor,
                                                    Layout layout,
                                                    double justified) {
        HBox box = new HBox();
        Label labelText = new Label(label);
        labelText.setAlignment(Pos.CENTER);
        labelText.setMinWidth(labelWidth);
        labelText.setPrefWidth(labelWidth);
        labelText.setMaxWidth(labelWidth);
        labelText.setPrefHeight(height);
        labelText.setStyle("-fx-background-color: -fx-inner-border, -fx-body-color;\n"
                           + "    -fx-background-insets: 0, 1;");
        box.getChildren()
           .add(labelText);
        Control control = buildControl(cardinality, layout);
        control.setPrefHeight(height);
        control.setPrefWidth(justified);
        box.getChildren()
           .add(control);
        box.setPrefWidth(justified);
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
        return columnWidth + layout.getTextHorizontalInset();
    }

    private void bind(Control control, TableColumn<JsonNode, ?> column,
                      double inset) {
        column.widthProperty()
              .addListener((o, prev, cur) -> {
                  double width = cur.doubleValue() - inset;
                  control.setMinWidth(width);
                  control.setMaxWidth(width);
              });
        double width = column.getWidth() - inset;
        control.setMinWidth(width);
        control.setMaxWidth(width);
    }

    private TextArea buildControl(int cardinality, Layout layout) {
        TextArea text = new TextArea();
        text.setWrapText(true);
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

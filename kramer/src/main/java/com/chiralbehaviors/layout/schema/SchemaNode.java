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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.Layout;
import com.chiralbehaviors.layout.Layout.SchemaNodeLayout;
import com.chiralbehaviors.layout.NestedTable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.util.Pair;

/**
 * @author hhildebrand
 *
 */
abstract public class SchemaNode {

    public static ArrayNode asArray(JsonNode node) {
        if (node == null) {
            return JsonNodeFactory.instance.arrayNode();
        }
        if (node.isArray()) {
            return (ArrayNode) node;
        }

        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        array.add(node);
        return array;
    }

    public static List<JsonNode> asList(JsonNode jsonNode) {
        List<JsonNode> nodes = new ArrayList<>();
        if (jsonNode == null) {
            return nodes;
        }
        if (jsonNode.isArray()) {
            jsonNode.forEach(node -> nodes.add(node));
        } else {
            return Collections.singletonList(jsonNode);
        }
        return nodes;
    }

    public static String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        boolean first = true;
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode row : ((ArrayNode) node)) {
                if (first) {
                    first = false;
                } else {
                    builder.append('\n');
                }
                builder.append(row.asText());
            }
            return builder.toString();
        }
        return node.asText();
    }

    public static ArrayNode extractField(JsonNode node, String field) {
        if (node == null) {
            return JsonNodeFactory.instance.arrayNode();
        }
        if (!node.isArray()) {
            JsonNode resolved = node.get(field);
            if (resolved == null) {
                return JsonNodeFactory.instance.arrayNode();
            }
            if (resolved.isArray()) {
                return (ArrayNode) resolved;
            }
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            array.add(resolved);
            return array;
        }
        ArrayNode extracted = JsonNodeFactory.instance.arrayNode();
        node.forEach(element -> {
            JsonNode resolved = element.get(field);
            if (resolved != null) {
                if (resolved.isArray()) {
                    extracted.addAll((ArrayNode) resolved);
                } else {
                    extracted.add(resolved);
                }
            }
        });
        return extracted;
    }

    public static List<JsonNode> extractList(JsonNode jsonNode, String field) {
        List<JsonNode> nodes = new ArrayList<>();
        if (jsonNode == null) {
            return nodes;
        }
        if (jsonNode.isArray()) {
            jsonNode.forEach(node -> nodes.add(node.get(field)));
        } else {
            return Collections.singletonList(jsonNode);
        }
        return nodes;
    }

    String field;

    Double height;
    Double justifiedWidth = 0D;
    String label;

    public SchemaNode() {
    }

    public SchemaNode(String field) {
        this(field, field);
    }

    public SchemaNode(String field, String label) {
        this.label = label;
        this.field = field;
    }

    abstract public Pair<Consumer<JsonNode>, Region> buildColumn(NestedTable table,
                                                                 double rendered,
                                                                 Layout layout);

    public Function<JsonNode, JsonNode> extract(Function<JsonNode, JsonNode> extractor) {
        return n -> {
            JsonNode extracted = extractor.apply(n);
            return extracted == null ? null : extracted.get(field);
        };
    }

    public JsonNode extractFrom(JsonNode jsonNode) {
        return extractField(jsonNode, field);
    }

    public String getField() {
        return field;
    }

    public Double getHeight() {
        return height;
    }

    public double getJustifiedWidth() {
        return justifiedWidth;
    }

    public String getLabel() {
        return label;
    }

    public double getLabelWidth() {
        return getLayout().labelWidth(label);
    }

    abstract public SchemaNodeLayout getLayout();

    public boolean isRelation() {
        return false;
    }

    public void setItems(Control control, JsonNode data, Layout layout) {
        layout.setItemsOf(control, data);
    }

    public void setLabel(String label) {
        this.label = label;
    }

    abstract public String toString(int indent);

    void adjustHeight(double delta) {
        this.height = Layout.snap(height + delta);
    }

    abstract double cellHeight(int cardinality, double available);

    abstract void compress(double available);

    Double getCalculatedHeight() {
        assert height != null : "cell height has not been calculated";
        return height;
    }

    boolean isUseTable() {
        return false;
    }

    abstract void justify(double width);

    Label label(double labelWidth) {
        Label labelText = new Label(label);
        labelText.setAlignment(Pos.CENTER);
        labelText.setMinWidth(labelWidth);
        labelText.setPrefHeight(height);
        labelText.setStyle("-fx-background-color: -fx-inner-border, -fx-body-color;\n"
                           + "    -fx-background-insets: 0, 1;");
        return labelText;
    }

    abstract double layout(int cardinality, double width);

    abstract double layoutWidth();

    abstract double measure(Relation parent, JsonNode data, boolean singular,
                            Layout layout);

    abstract Pair<Consumer<JsonNode>, Parent> outlineElement(int cardinality,
                                                             double labelWidth,
                                                             Function<JsonNode, JsonNode> extractor,
                                                             Layout layout,
                                                             double justified);

    abstract double outlineWidth();

    abstract double rowHeight(int cardinality, double justified);

    abstract double tableColumnWidth();
}

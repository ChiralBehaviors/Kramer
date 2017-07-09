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
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.chiralbehaviors.layout.Layout;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.TableColumn;
import javafx.util.Pair;

/**
 * @author hhildebrand
 *
 */
@JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.NONE)
abstract public class SchemaNode {

    protected static enum INDENT {
        LEFT,
        NONE,
        RIGHT {
            @Override
            public boolean isRight() {
                return true;
            }
        };

        public boolean isRight() {
            return false;
        }
    }

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
        if (node == null) {
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
            if (resolved.isArray()) {
                extracted.addAll((ArrayNode) resolved);
            } else {
                extracted.add(resolved);
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

    public static double labelHeight(Layout layout) {
        return Math.max(43, Layout.snap(layout.getTextLineHeight() * 2)
                            + layout.getTextVerticalInset());
    }

    String field;
    double justifiedWidth = 0;
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

    public JsonNode extractFrom(JsonNode jsonNode) {
        return extractField(jsonNode, field);
    }

    public String getField() {
        return field;
    }

    public String getLabel() {
        return label;
    }

    public double getLabelWidth(Layout layout) {
        return layout.textWidth(label);
    }

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

    abstract Supplier<Pair<Consumer<JsonNode>, Control>> buildColumn(int cardinality,
                                                                     Function<JsonNode, JsonNode> extractor,
                                                                     Map<SchemaNode, TableColumn<JsonNode, ?>> columnMap,
                                                                     Layout layout,
                                                                     double inset,
                                                                     INDENT indent,
                                                                     double justified);

    TableColumn<JsonNode, JsonNode> buildColumn(Layout layout, double inset,
                                                INDENT indent, double width) {
        TableColumn<JsonNode, JsonNode> column = new TableColumn<>(label);
        column.setUserData(this);
        return column;
    }

    abstract double cellHeight(Layout layout, double available);

    void compress(Layout layout, double available) {
    }

    Function<JsonNode, JsonNode> extract(Function<JsonNode, JsonNode> extractor) {
        return n -> {
            JsonNode extracted = extractor.apply(n);
            return extracted == null ? null : extracted.get(field);
        };
    }

    boolean isUseTable() {
        return false;
    }

    abstract void justify(double width, Layout layout);

    abstract double layout(int cardinality, Layout layout, double width);

    abstract double layoutWidth(Layout layout);

    abstract double measure(ArrayNode data, Layout layout, INDENT indent);

    abstract Pair<Consumer<JsonNode>, Parent> outlineElement(double labelWidth,
                                                             Function<JsonNode, JsonNode> extractor,
                                                             double cellHeight,
                                                             int cardinality,
                                                             Layout layout,
                                                             double justified);

    abstract double outlineWidth(Layout layout);

    abstract double rowHeight(Layout layout, double justified);

    abstract double tableColumnWidth(Layout layout);
}

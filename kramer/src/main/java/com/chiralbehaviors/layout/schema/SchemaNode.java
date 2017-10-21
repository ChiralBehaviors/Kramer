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

import com.chiralbehaviors.layout.LayoutProvider;
import com.chiralbehaviors.layout.SchemaNodeLayout;
import com.chiralbehaviors.layout.SchemaNodeLayout.INDENT;
import com.chiralbehaviors.layout.control.NestedTable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.Parent;
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

    public void adjustHeight(double delta) {
        getLayout().adjustHeight(delta);
    }

    abstract public Pair<Consumer<JsonNode>, Region> buildColumn(NestedTable table,
                                                                 double rendered);

    abstract public Function<Double, Region> buildColumnHeader(INDENT indent);

    public abstract double cellHeight(int cardinality, double available);

    public double columnHeaderHeight() {
        return getLayout().columnHeaderHeight();
    }

    public abstract void compress(double available);

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

    public abstract double justify(double width);

    public abstract double layout(int cardinality, double width);

    public abstract double layoutWidth();

    public abstract double measure(Relation parent, JsonNode data,
                                   boolean singular, LayoutProvider layout);

    public abstract Pair<Consumer<JsonNode>, Parent> outlineElement(int cardinality,
                                                                    double labelWidth,
                                                                    Function<JsonNode, JsonNode> extractor,
                                                                    double justified);

    public abstract double rowHeight(int cardinality, double justified);

    public void setLabel(String label) {
        this.label = label;
    }

    public abstract double tableColumnWidth();

    abstract public String toString(int indent);
}

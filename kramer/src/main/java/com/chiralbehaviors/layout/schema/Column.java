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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 * 
 * @author halhildebrand
 *
 */
public class Column {

    private ArrayDeque<SchemaNode> fields = new ArrayDeque<>();
    private double                 width  = 0;

    public Column(double width) {
        this.width = width;
    }

    public void add(SchemaNode node) {
        fields.add(node);
    }

    public void addFirst(SchemaNode field) {
        fields.addFirst(field);
    }

    public double cellHeight(int cardinality, double labelWidth) {
        return cellHeight(cardinality, fields, labelWidth);
    }

    public double maxWidth(double labelWidth) {
        return fields.stream()
                     .mapToDouble(field -> labelWidth + field.layoutWidth())
                     .max()
                     .orElse(0d);
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public boolean slideRight(int cardinality, Column column,
                              double columnWidth, double labelWidth) {
        if (fields.size() < 1) {
            return false;
        }
        if (column.fields.isEmpty()) {
            column.addFirst(fields.removeLast());
            return true;
        }
        if (without(cardinality, labelWidth) < column.with(cardinality,
                                                           fields.getLast(),
                                                           labelWidth)) {
            return false;
        }
        column.addFirst(fields.removeLast());
        return true;
    }

    @Override
    public String toString() {
        return String.format("Column [%s] [fields=%s]", width, fields.stream()
                                                                     .map(p -> p.getField())
                                                                     .collect(Collectors.toList()));
    }

    void adjustHeight(double distributed) {
        double delta = distributed / fields.size();
        if (delta >= 1.0) {
            fields.forEach(f -> f.adjustHeight(delta));
        }
    }

    Consumer<JsonNode> build(double cellHeight, VBox column, int cardinality,
                             Function<JsonNode, JsonNode> extractor,
                             double labelWidth) {
        List<Consumer<JsonNode>> controls = new ArrayList<>();
        fields.forEach(field -> {
            Pair<Consumer<JsonNode>, Parent> master = field.outlineElement(cardinality,
                                                                           labelWidth,
                                                                           extractor,
                                                                           width);
            controls.add(master.getKey());
            column.getChildren()
                  .add(master.getValue());
        });
        return item -> controls.forEach(m -> m.accept(item));
    }

    void distributeHeight(double finalHeight) {
        double calculated = fields.stream()
                                  .mapToDouble(f -> f.getCalculatedHeight())
                                  .sum();
        if (calculated < finalHeight) {
            double delta = (finalHeight - calculated) / fields.size();
            if (delta >= 1.0) {
                fields.forEach(f -> f.adjustHeight(delta));
            }
        }
    }

    List<SchemaNode> getFields() {
        return Arrays.stream(fields.toArray())
                     .map(f -> (SchemaNode) f)
                     .collect(Collectors.toList());
    }

    double getWidth() {
        return width;
    }

    private double cellHeight(int cardinality, ArrayDeque<SchemaNode> elements,
                              double labelWidth) {
        double available = width - labelWidth;
        return elements.stream()
                       .mapToDouble(field -> field.cellHeight(cardinality,
                                                              available))
                       .reduce((a, b) -> a + b)
                       .orElse(0d);
    }

    private double with(int cardinality, SchemaNode field, double labelWidth) {
        ArrayDeque<SchemaNode> elements = fields.clone();
        elements.add(field);
        return cellHeight(cardinality, elements, labelWidth);
    }

    private double without(int cardinality, double labelWidth) {
        ArrayDeque<SchemaNode> elements = fields.clone();
        elements.removeLast();
        return cellHeight(cardinality, elements, labelWidth);
    }
}
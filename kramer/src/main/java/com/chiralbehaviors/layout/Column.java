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

package com.chiralbehaviors.layout;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.chiralbehaviors.layout.schema.SchemaNode;

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

    public List<SchemaNode> getFields() {
        return Arrays.stream(fields.toArray())
                     .map(f -> (SchemaNode) f)
                     .collect(Collectors.toList());
    }

    public double getWidth() {
        return width;
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

    void distributeHeight(double finalHeight) {
        double calculated = fields.stream()
                                  .mapToDouble(f -> f.getLayout()
                                                     .getHeight())
                                  .sum();
        if (calculated < finalHeight) {
            double delta = (finalHeight - calculated) / fields.size();
            if (delta >= 1.0) {
                fields.forEach(f -> f.adjustHeight(delta));
            }
        }
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
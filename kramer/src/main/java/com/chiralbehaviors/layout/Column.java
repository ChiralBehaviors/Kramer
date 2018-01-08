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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.chiralbehaviors.layout.style.Layout;
import com.chiralbehaviors.layout.style.RelationStyle;

/**
 *
 * @author halhildebrand
 *
 */
public class Column {

    private ArrayDeque<SchemaNodeLayout> fields = new ArrayDeque<>();

    public Column() {
    }

    public void add(SchemaNodeLayout node) {
        fields.add(node);
    }

    public void addFirst(SchemaNodeLayout field) {
        fields.addFirst(field);
    }

    public double cellHeight(int cardinality, RelationStyle style,
                             double fieldWidth) {
        return cellHeight(cardinality, fields, style, fieldWidth);
    }

    public List<SchemaNodeLayout> getFields() {
        return new ArrayList<>(fields);
    }

    public double maxWidth(double labelWidth) {
        return fields.stream()
                     .mapToDouble(field -> labelWidth + field.layoutWidth())
                     .max()
                     .orElse(0d);
    }

    public boolean slideRight(int cardinality, Column column,
                              RelationStyle style, double fieldWidth) {
        if (fields.size() < 1) {
            return false;
        }
        if (column.fields.isEmpty()) {
            column.addFirst(fields.removeLast());
            return true;
        }
        if (without(cardinality, style,
                    fieldWidth) < column.with(cardinality, fields.getLast(),
                                              style, fieldWidth)) {
            return false;
        }
        column.addFirst(fields.removeLast());
        return true;
    }

    @Override
    public String toString() {
        return String.format("Column %s", fields.stream()
                                                .map(p -> p.getField())
                                                .collect(Collectors.toList()));
    }

    void adjustHeight(double distributed) {
        double delta = distributed / fields.size();
        if (delta >= 1.0) {
            fields.forEach(f -> f.adjustHeight(delta));
        }
    }

    void distributeHeight(double finalHeight, RelationStyle style) {
        double calculated = fields.stream()
                                  .mapToDouble(f -> Layout.snap(f.getHeight()
                                                                + style.getElementVerticalInset()))
                                  .sum();
        if (calculated < finalHeight) {
            double delta = Layout.snap((finalHeight - calculated)
                                       / (double) fields.size());
            if (delta >= 1.0) {
                fields.forEach(f -> f.adjustHeight(delta));
            }
        }
    }

    private double cellHeight(int cardinality,
                              ArrayDeque<SchemaNodeLayout> elements,
                              RelationStyle style, double fieldWidth) {
        return elements.stream()
                       .mapToDouble(field -> Layout.snap(field.cellHeight(cardinality,
                                                                          fieldWidth)
                                                         + style.getElementVerticalInset()))
                       .sum();
    }

    private double with(int cardinality, SchemaNodeLayout field,
                        RelationStyle style, double fieldWidth) {
        ArrayDeque<SchemaNodeLayout> elements = fields.clone();
        elements.add(field);
        return cellHeight(cardinality, elements, style, fieldWidth);
    }

    private double without(int cardinality, RelationStyle style,
                           double fieldWidth) {
        ArrayDeque<SchemaNodeLayout> elements = fields.clone();
        elements.removeLast();
        return cellHeight(cardinality, elements, style, fieldWidth);
    }
}
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.chiralbehaviors.layout.Layout;

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

    public double elementHeight(int cardinality, Layout layout) {
        return elementHeight(cardinality, layout, fields);
    }

    public double maxWidth(Layout layout) {
        return fields.stream()
                     .mapToDouble(field -> field.getTableColumnWidth(layout))
                     .max()
                     .orElse(0d);
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public boolean slideRight(int cardinality, Column column, Layout layout,
                              double columnWidth) {
        if (fields.size() < 1) {
            return false;
        } 
        if (column.fields.isEmpty()) {
            column.addFirst(fields.removeLast());
            return true;
        }
        if (without(cardinality, layout) < column.with(fields.getLast(),
                                                       cardinality, layout)) {
            return false;
        }
        column.addFirst(fields.removeLast());
        return true;
    }

    @Override
    public String toString() {
        return String.format("Column [fields=%s %s]", fields.stream()
                                                            .map(p -> p.getField())
                                                            .collect(Collectors.toList()),
                             width);
    }

    //for testing
    List<SchemaNode> getFields() {
        return Arrays.stream(fields.toArray())
                     .map(f -> (SchemaNode) f)
                     .collect(Collectors.toList());
    }

    private double elementHeight(int cardinality, Layout layout,
                                 ArrayDeque<SchemaNode> elements) {
        double available = width - elements.stream()
                                           .mapToDouble(field -> field.getLabelWidth(layout))
                                           .max()
                                           .orElse(0d);
        return Layout.snap(elements.stream()
                                   .mapToDouble(field -> {
                                       return field.elementHeight(cardinality,
                                                                  layout,
                                                                  available);
                                   })
                                   .reduce((a, b) -> a + b)
                                   .orElse(0d));
    }

    private double with(SchemaNode field, int cardinality, Layout layout) {
        ArrayDeque<SchemaNode> elements = fields.clone();
        elements.add(field);
        return elementHeight(cardinality, layout, elements);
    }

    private double without(int cardinality, Layout layout) {
        ArrayDeque<SchemaNode> elements = fields.clone();
        elements.removeLast();
        return elementHeight(cardinality, layout, elements);
    }
}
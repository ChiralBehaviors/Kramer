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

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.style.RelationStyle;

/**
 *
 * @author halhildebrand
 *
 */
public class ColumnSet {

    private final List<Column> columns = new ArrayList<>();

    {
        columns.add(new Column());
    }

    public void add(SchemaNodeLayout node) {
        columns.get(0)
               .add(node);
    }

    /** Package-private: append a pre-built Column. Used by compress() and tests. */
    void addColumn(Column column) {
        columns.add(column);
    }

    public void adjustHeight(double delta) {
        columns.forEach(c -> c.adjustHeight(delta));
    }

    public double compress(int cardinality, double justified,
                           RelationStyle style, double labelWidth) {
        return compress(cardinality, justified, style, labelWidth,
                        ColumnPartitioner.dpOptimal());
    }

    double compress(int cardinality, double justified,
                    RelationStyle style, double labelWidth,
                    ColumnPartitioner partitioner) {
        Column firstColumn = columns.get(0);
        // Minimum column width must accommodate label + insets + minimum
        // data space. Without this, labelWidth can consume the entire
        // column leaving zero space for data values.
        double minDataWidth = 30.0; // minimum space for any visible data
        double minColumnWidth = Math.max(
            firstColumn.maxWidth(labelWidth)
                + style.getElementHorizontalInset()
                + style.getColumnHorizontalInset(),
            Math.max(style.getOutlineColumnMinWidth(),
                     labelWidth + minDataWidth
                     + style.getElementHorizontalInset()
                     + style.getColumnHorizontalInset()));
        int count = min(firstColumn.getFields()
                                   .size(),
                        max(1,
                            (int) Math.floor(justified / minColumnWidth)));

        // compression
        double columnWidth = Math.floor(justified / (double) count);
        double fieldWidth = Math.max(0.0, Style.snap(columnWidth - labelWidth
                                       - style.getElementHorizontalInset()
                                       - style.getColumnHorizontalInset()));
        firstColumn.setWidth(columnWidth);
        firstColumn.getFields()
                   .forEach(f -> {
                       f.compress(fieldWidth);
                   });

        if (count <= 1) {
            // Single column — no partitioning needed
            double finalHeight = Style.snap(
                firstColumn.cellHeight(cardinality, style, fieldWidth));
            firstColumn.distributeHeight(finalHeight, style);
            return Style.snap(finalHeight + style.getColumnVerticalInset());
        }

        // Compute per-field heights for the partitioner
        List<SchemaNodeLayout> fields = firstColumn.getFields();
        double[] fieldHeights = new double[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            fieldHeights[i] = Style.snap(
                fields.get(i).cellHeight(cardinality, fieldWidth)
                + style.getElementVerticalInset());
        }

        // Partition fields into columns
        int[] sizes = partitioner.partition(fieldHeights, count);

        // Build columns from partition
        columns.clear();
        int idx = 0;
        for (int col = 0; col < count; col++) {
            Column c = new Column(columnWidth);
            for (int j = 0; j < sizes[col] && idx < fields.size(); j++) {
                c.add(fields.get(idx++));
            }
            columns.add(c);
        }

        double baseHeight = columns.stream()
                                   .mapToDouble(c -> c.cellHeight(cardinality,
                                                                  style,
                                                                  fieldWidth))
                                   .max()
                                   .orElse(0d);
        double finalHeight = Style.snap(baseHeight);
        columns.forEach(c -> c.distributeHeight(finalHeight, style));
        return Style.snap(finalHeight + style.getColumnVerticalInset());
    }

    public List<Column> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public ColumnSetSnapshot toSnapshot(double height) {
        var columnSnapshots = columns.stream()
                                     .map(c -> new ColumnSnapshot(c.getWidth(),
                                                                   c.getFields()
                                                                    .stream()
                                                                    .map(SchemaNodeLayout::getField)
                                                                    .toList()))
                                     .toList();
        return new ColumnSetSnapshot(columnSnapshots, height);
    }

    @Override
    public String toString() {
        return String.format("ColumnSet [%s]", columns);
    }
}
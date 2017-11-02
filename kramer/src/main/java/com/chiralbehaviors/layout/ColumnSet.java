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
import java.util.List;
import java.util.stream.IntStream;

import com.chiralbehaviors.layout.schema.SchemaNode;

/**
 *
 * @author halhildebrand
 *
 */
public class ColumnSet {

    private double             cellHeight;
    private final List<Column> columns = new ArrayList<>();

    {
        columns.add(new Column(0d));
    }

    public void add(SchemaNode node) {
        columns.get(0)
               .add(node);
    }

    public void adjustHeight(double delta) {
        cellHeight = LayoutProvider.snap(cellHeight + delta);
        columns.forEach(c -> c.adjustHeight(delta));
    }

    public void compress(int cardinality, double justified, double labelWidth,
                         boolean scrolled) {
        Column firstColumn = columns.get(0);
        int count = min(firstColumn.getFields()
                                   .size(),
                        max(1, (int) (justified
                                      / firstColumn.maxWidth(labelWidth))));
        double fieldWidth = justified - labelWidth;
        if (count == 1) {
            firstColumn.setWidth(justified);
            firstColumn.getFields()
                       .forEach(f -> {
                           f.compress(fieldWidth, scrolled);
                       });
            cellHeight = firstColumn.cellHeight(cardinality, labelWidth);
            return;
        }

        // compression
        double columnWidth = justified / count;
        firstColumn.setWidth(columnWidth);
        double compressed = columnWidth - labelWidth;
        firstColumn.getFields()
                   .forEach(f -> {
                       f.compress(compressed, scrolled);
                   });
        IntStream.range(1, count)
                 .forEach(i -> columns.add(new Column(columnWidth)));
        cellHeight = firstColumn.cellHeight(cardinality, labelWidth);
        double lastHeight;
        do {
            lastHeight = cellHeight;
            for (int i = 0; i < columns.size() - 1; i++) {
                while (columns.get(i)
                              .slideRight(cardinality, columns.get(i + 1),
                                          columnWidth, labelWidth)) {
                }
            }
            cellHeight = columns.stream()
                                .mapToDouble(c -> c.cellHeight(cardinality,
                                                               labelWidth))
                                .max()
                                .orElse(0d);
        } while (lastHeight > cellHeight);
        columns.forEach(c -> c.distributeHeight(cellHeight));
    }

    public double getCellHeight() {
        return cellHeight;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public double getWidth() {
        return columns.stream()
                      .mapToDouble(c -> c.getWidth())
                      .sum();
    }

    @Override
    public String toString() {
        return String.format("ColumnSet [%s] [%s]", cellHeight, columns);
    }
}
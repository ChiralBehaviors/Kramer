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

import com.chiralbehaviors.layout.style.LayoutModel;
import com.chiralbehaviors.layout.style.RelationStyle;

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

    public void add(SchemaNodeLayout node) {
        columns.get(0)
               .add(node);
    }

    public void adjustHeight(double delta) {
        cellHeight = LayoutModel.snap(cellHeight + delta);
        columns.forEach(c -> c.adjustHeight(delta));
    }

    public void compress(int cardinality, double available, RelationStyle style,
                         double labelWidth) {
        double justified = available - style.getSpanHorizontalInset();
        Column firstColumn = columns.get(0);
        int count = min(firstColumn.getFields()
                                   .size(),
                        max(1,
                            (int) (justified
                                   / (firstColumn.maxWidth(labelWidth)
                                      + style.getColumnHorizontalInset()))));
        double fieldWidth = justified - labelWidth
                            - style.getColumnHorizontalInset();
        if (count == 1) {
            firstColumn.setWidth(justified - style.getColumnHorizontalInset());
            firstColumn.getFields()
                       .forEach(f -> {
                           f.compress(fieldWidth);
                       });
            cellHeight = firstColumn.cellHeight(cardinality, labelWidth)
                         + style.getSpanVerticalInset();
            return;
        }

        // compression
        double columnWidth = (justified / (double) count)
                             - style.getColumnHorizontalInset();
        firstColumn.setWidth(columnWidth);
        double compressed = LayoutModel.relax(columnWidth - labelWidth
                                              - style.getElementHorizontalInset());
        firstColumn.getFields()
                   .forEach(f -> {
                       f.compress(compressed);
                   });
        IntStream.range(1, count)
                 .forEach(i -> columns.add(new Column(columnWidth)));
        double baseHeight = firstColumn.cellHeight(cardinality, labelWidth);
        double lastHeight;
        do {
            lastHeight = baseHeight;
            for (int i = 0; i < columns.size() - 1; i++) {
                while (columns.get(i)
                              .slideRight(cardinality, columns.get(i + 1),
                                          labelWidth)) {
                }
            }
            baseHeight = columns.stream()
                                .mapToDouble(c -> c.cellHeight(cardinality,
                                                               labelWidth))
                                .max()
                                .orElse(0d);
        } while (lastHeight > baseHeight);
        double finalHeight = baseHeight;
        columns.forEach(c -> c.distributeHeight(finalHeight));
        cellHeight = finalHeight + style.getColumnVerticalInset() + style.getSpanVerticalInset();
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
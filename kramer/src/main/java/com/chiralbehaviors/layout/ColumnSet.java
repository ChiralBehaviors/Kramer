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

import com.chiralbehaviors.layout.style.Layout;
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

    public void adjustHeight(double delta) {
        columns.forEach(c -> c.adjustHeight(delta));
    }

    public double compress(int cardinality, double justified,
                           RelationStyle style, double labelWidth) {
        Column firstColumn = columns.get(0);
        int count = min(firstColumn.getFields()
                                   .size(),
                        max(1,
                            (int) Math.floor(justified
                                             / (firstColumn.maxWidth(labelWidth)
                                                + style.getElementHorizontalInset()
                                                + style.getColumnHorizontalInset()))));
        if (count == 1) {
            double fieldWidth = Layout.snap(justified - labelWidth
                                            - style.getElementHorizontalInset()
                                            - style.getColumnHorizontalInset());
            firstColumn.getFields()
                       .forEach(f -> {
                           f.compress(fieldWidth);
                       });
            return firstColumn.cellHeight(cardinality, style, fieldWidth)
                   + style.getColumnVerticalInset();
        }

        // compression
        double columnWidth = justified / (double) count;
        double fieldWidth = Layout.snap(columnWidth - labelWidth
                                        - style.getElementHorizontalInset()
                                        - style.getColumnHorizontalInset());
        firstColumn.getFields()
                   .forEach(f -> {
                       f.compress(fieldWidth);
                   });
        IntStream.range(1, count)
                 .forEach(i -> columns.add(new Column()));
        double baseHeight = firstColumn.cellHeight(cardinality, style,
                                                   fieldWidth);
        double lastHeight;
        do {
            lastHeight = baseHeight;
            for (int i = 0; i < columns.size() - 1; i++) {
                while (columns.get(i)
                              .slideRight(cardinality, columns.get(i + 1),
                                          style, fieldWidth)) {
                }
            }
            baseHeight = columns.stream()
                                .mapToDouble(c -> c.cellHeight(cardinality,
                                                               style,
                                                               fieldWidth))
                                .max()
                                .orElse(0d);
        } while (lastHeight > baseHeight);
        double finalHeight = Layout.snap(baseHeight);
        columns.forEach(c -> c.distributeHeight(finalHeight, style));
        return finalHeight + style.getColumnVerticalInset();
    }

    public List<Column> getColumns() {
        return columns;
    }

    @Override
    public String toString() {
        return String.format("ColumnSet [%s]", columns);
    }
}
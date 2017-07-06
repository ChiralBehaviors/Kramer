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

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import com.chiralbehaviors.layout.Layout;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.Parent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 * 
 * @author halhildebrand
 *
 */
public class ColumnSet {

    private double             cellHeight;
    private final List<Column> columns = new ArrayList<>();
    private final double       labelWidth;

    {
        columns.add(new Column(0d));
    }

    public ColumnSet(double labelWidth) {
        this.labelWidth = labelWidth;
    }

    public void add(SchemaNode node) {
        columns.get(0)
               .add(node);
    }

    public void compress(Layout layout, double width) {
        Column firstColumn = columns.get(0);
        int count = min(firstColumn.getFields()
                                   .size(),
                        max(1,
                            (int) (width / firstColumn.maxWidth(layout,
                                                                labelWidth))));

        if (count == 1) {
            firstColumn.setWidth(width);
            firstColumn.getFields()
                       .forEach(f -> f.compress(layout, width - labelWidth));
            cellHeight = firstColumn.cellHeight(layout, labelWidth);
            return;
        }

        // compression
        double columnWidth = Layout.snap(width / count);
        firstColumn.getFields()
                   .forEach(f -> f.compress(layout, columnWidth - labelWidth));
        firstColumn.setWidth(columnWidth);
        IntStream.range(1, count)
                 .forEach(i -> columns.add(new Column(columnWidth)));
        cellHeight = firstColumn.cellHeight(layout, labelWidth);
        double lastHeight;
        do {
            lastHeight = cellHeight;
            for (int i = 0; i < columns.size() - 1; i++) {
                while (columns.get(i)
                              .slideRight(columns.get(i + 1), layout,
                                          columnWidth, labelWidth)) {
                }
            }
            cellHeight = columns.stream()
                                .mapToDouble(c -> c.cellHeight(layout,
                                                               labelWidth))
                                .max()
                                .orElse(0d);
        } while (lastHeight > cellHeight);
    }

    public double getCellHeight() {
        return cellHeight;
    }

    @Override
    public String toString() {
        return String.format("ColumnSet [%s] [%s]", cellHeight, columns);
    }

    Pair<Consumer<JsonNode>, Parent> build(Function<JsonNode, JsonNode> extractor,
                                           Layout layout, double justified) {
        HBox span = new HBox(); 
        span.setPrefWidth(justified); 
        span.setPrefHeight(cellHeight);
        List<Consumer<JsonNode>> controls = new ArrayList<>();
        columns.forEach(c -> {
            VBox cell = new VBox();
            controls.add(c.build(cellHeight, cell, extractor, layout,
                                 labelWidth)); 
            span.getChildren()
                .add(cell);
        });
        return new Pair<>(item -> controls.forEach(c -> c.accept(item)), span);
    }

    List<Column> getColumns() {
        return columns;
    }
}
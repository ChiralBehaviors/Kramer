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

import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.Layout;
import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.control.NestedTable;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.util.Pair;

/**
 * @author hhildebrand
 *
 */
public class Primitive extends SchemaNode {

    private PrimitiveLayout layout;
    private double          defaultWidth = 0;

    public Primitive() {
        super();
    }

    public Primitive(String label) {
        super(label);
    }

    @Override
    public Pair<Consumer<JsonNode>, Region> buildColumn(NestedTable table,
                                                        double rendered) {
        return table.buildPrimitive(rendered, this);
    }

    @Override
    public double cellHeight(int cardinality, double justified) {
        return layout.cellHeight(justified);
    }

    @Override
    public void compress(double available) {
        layout.compress(available);
    }

    @Override
    public PrimitiveLayout getLayout() {
        return layout;
    }

    @Override
    public double justify(double available) {
        return layout.justify(available);
    }

    @Override
    public double layoutWidth() {
        return tableColumnWidth();
    }

    @Override
    public Pair<Consumer<JsonNode>, Parent> outlineElement(int cardinality,
                                                           double labelWidth,
                                                           Function<JsonNode, JsonNode> extractor,
                                                           double justified) {
        return layout.outlineElement(field, cardinality, label, labelWidth,
                                     extractor, justified);
    }

    @Override
    public double tableColumnWidth() {
        return layout.tableColumnWidth();
    }

    @Override
    public String toString() {
        return String.format("Primitive [%s]", label);
    }

    @Override
    public String toString(int indent) {
        return toString();
    }

    @Override
    public double layout(int cardinality, double width) {
        return layout.layout(cardinality, width);
    }

    @Override
    public double measure(Relation parent, JsonNode data, boolean singular,
                          Layout l) {
        layout = l.layout(this);
        return layout.measure(parent, data, singular);
    }

    @Override
    double outlineWidth() {
        return tableColumnWidth();
    }

    @Override
    public double rowHeight(int cardinality, double width) {
        return cellHeight(cardinality, width);
    }

    public double getDefaultWidth() {
        return defaultWidth;
    }
}

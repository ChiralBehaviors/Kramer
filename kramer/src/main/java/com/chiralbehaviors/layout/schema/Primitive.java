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

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.Layout;
import com.chiralbehaviors.layout.Layout.PrimitiveLayout;
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

    private double          columnWidth       = 0;
    private double          maxWidth          = 0;
    private PrimitiveLayout pLayout;
    private double          valueDefaultWidth = 0;
    private boolean         variableLength    = false;

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
        if (height > 0) {
            return height;
        }
        height = pLayout.cellHeight(maxWidth, justified);
        return height;
    }

    @Override
    public void compress(double available) {
        justifiedWidth = pLayout.baseOutlineWidth(available);
    }

    @Override
    public PrimitiveLayout getLayout() {
        return pLayout;
    }

    @Override
    public double justify(double available) {
        justifiedWidth = pLayout.baseTableColumnWidth(available);
        return justifiedWidth;
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
        return pLayout.outlineElement(field, cardinality, height, label,
                                      labelWidth, extractor, justified);
    }

    @Override
    public double tableColumnWidth() {
        return pLayout.tableColumnWidth(columnWidth);
    }

    @Override
    public String toString() {
        return String.format("Primitive [%s:%.2f:%.2f]", label, columnWidth,
                             justifiedWidth);
    }

    @Override
    public String toString(int indent) {
        return toString();
    }

    @Override
    double layout(int cardinality, double width) {
        height = -1.0;
        return variableLength ? width : Math.min(width, columnWidth);
    }

    @Override
    double measure(Relation parent, JsonNode data, boolean singular,
                   Layout layout) {
        pLayout = layout.layout(this);
        double labelWidth = getLabelWidth();
        double sum = 0;
        maxWidth = 0;
        columnWidth = 0;
        justifiedWidth = -1.0;
        for (JsonNode prim : SchemaNode.asList(data)) {
            List<JsonNode> rows = SchemaNode.asList(prim);
            double width = 0;
            for (JsonNode row : rows) {
                width += pLayout.width(row);
                maxWidth = Math.max(maxWidth, width);
            }
            sum += rows.isEmpty() ? 1 : width / rows.size();
        }
        double averageWidth = data.size() == 0 ? 0 : (sum / data.size());

        columnWidth = Layout.snap(Math.max(labelWidth,
                                           Math.max(valueDefaultWidth,
                                                    averageWidth)));
        if (maxWidth > averageWidth) {
            variableLength = true;
        }

        return pLayout.tableColumnWidth(columnWidth);
    }

    @Override
    double outlineWidth() {
        return tableColumnWidth();
    }

    @Override
    double rowHeight(int cardinality, double width) {
        return cellHeight(cardinality, width);
    }
}

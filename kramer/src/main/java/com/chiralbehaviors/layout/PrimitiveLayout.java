/**
 * Copyright (c) 2017 Chiral Behaviors, LLC, all rights reserved.
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

import static com.chiralbehaviors.layout.LayoutProvider.snap;
import static com.chiralbehaviors.layout.schema.SchemaNode.asList;

import java.util.List;
import java.util.function.Function;

import com.chiralbehaviors.layout.cell.FocusTraversal;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.outline.OutlineElement;
import com.chiralbehaviors.layout.primitives.LabelCell;
import com.chiralbehaviors.layout.primitives.PrimitiveList;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.table.ColumnHeader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.geometry.Insets;
import javafx.scene.layout.Region;

/**
 *
 * @author halhildebrand
 *
 */
public class PrimitiveLayout extends SchemaNodeLayout {
    protected int          averageCardinality;
    protected final Insets listInsets;
    protected double       maxWidth;
    private double         cellHeight;
    @SuppressWarnings("unused")
    private boolean        variableLength;

    public PrimitiveLayout(LayoutProvider layout, Primitive p) {
        super(layout, p);
        this.listInsets = layout.listInsets(this);
    }

    public void apply(LayoutCell<?> cell) {
        layout.getModel()
              .apply(cell, getNode());
    }

    public LayoutCell<?> buildCell(FocusTraversal pt) {
        LabelCell cell = new LabelCell(this);
        cell.getNode()
            .getStyleClass()
            .add(node.getField());
        cell.getNode()
            .setMinSize(justifiedWidth, cellHeight);
        cell.getNode()
            .setPrefSize(justifiedWidth, cellHeight);
        cell.getNode()
            .setMaxSize(justifiedWidth, cellHeight);
        return cell;
    }

    @Override
    public LayoutCell<? extends Region> buildColumn(double rendered,
                                                    FocusTraversal parentTraversal) {
        LayoutCell<? extends Region> control = buildControl(parentTraversal);
        control.getNode()
               .setMinSize(justifiedWidth, rendered);
        control.getNode()
               .setPrefSize(justifiedWidth, rendered);
        control.getNode()
               .setMaxSize(justifiedWidth, rendered);
        return control;
    }

    @Override
    public LayoutCell<? extends Region> buildControl(FocusTraversal parentTraversal) {
        return averageCardinality > 1 ? new PrimitiveList(this, parentTraversal)
                                      : buildCell(parentTraversal);
    }

    @Override
    public void calculateCellHeight() {
        cellHeight(averageCardinality, justifiedWidth);
    }

    @Override
    public double calculateTableColumnWidth() {
        return columnWidth();
    }

    @Override
    public double cellHeight(int cardinality, double justified) {
        if (height > 0) {
            return height;
        }
        int resolvedCardinality = Math.min(cardinality, averageCardinality);
        double rows = Math.ceil((maxWidth / justified) + 0.5);
        boolean list = resolvedCardinality > 1;
        cellHeight = snap((layout.getTextLineHeight() * rows)
                          + layout.getTextVerticalInset());
        if (list) {
            height = (cellHeight * resolvedCardinality) + listInsets.getTop()
                     + listInsets.getBottom();
        } else {
            height = cellHeight;
        }
        return height;
    }

    @Override
    public Function<Double, ColumnHeader> columnHeader() {
        return rendered -> new ColumnHeader(snap(justifiedWidth
                                                 + columnHeaderIndentation),
                                            rendered, this);
    }

    @Override
    public double columnWidth() {
        return Math.max(columnWidth, labelWidth);
    }

    @Override
    public void compress(double available) {
        justifiedWidth = snap(available);
    }

    @Override
    public JsonNode extractFrom(JsonNode datum) {
        return node.extractFrom(datum);
    }

    public double getCellHeight() {
        return cellHeight;
    }

    @Override
    public double getJustifiedColumnWidth() {
        return snap(justifiedWidth);
    }

    @Override
    public double justify(double justified) {
        justifiedWidth = snap(justified);
        return justifiedWidth;
    }

    @Override
    public double layout(double width) {
        clear();
        return width;
    }

    @Override
    public double layoutWidth() {
        return columnWidth();
    }

    @Override
    public SchemaNodeLayout measure(JsonNode datum) {
        ArrayNode setOf = JsonNodeFactory.instance.arrayNode();
        setOf.add(datum);
        measure(setOf, n -> n);
        return this;
    }

    @Override
    public double measure(JsonNode data,
                          Function<JsonNode, JsonNode> extractor) {
        clear();
        labelWidth = labelWidth(node.getLabel());
        double summedDataWidth = 0;
        maxWidth = 0;
        columnWidth = 0;
        List<JsonNode> normalized = asList(data);
        int cardSum = 0;
        for (JsonNode prim : normalized) {
            if (prim.isArray()) {
                cardSum += prim.size();
                double summedWidth = 0;
                for (JsonNode row : prim) {
                    double w = width(row);
                    summedWidth += w;
                    maxWidth = Math.max(maxWidth, w);
                }
                summedDataWidth += prim.size() == 0 ? 1
                                                    : summedWidth / prim.size();
            } else {
                cardSum += 1;
                double w = width(prim);
                summedDataWidth += w;
                maxWidth = Math.max(maxWidth, w);
            }
        }
        double averageWidth = 0;
        averageCardinality = 1;
        if (data.size() > 0) {
            averageCardinality = cardSum / data.size();
            averageWidth = summedDataWidth / data.size();
        }

        columnWidth = Math.max(labelWidth,
                               LayoutProvider.snap(Math.max(getNode().getDefaultWidth(),
                                                            averageWidth)));
        if (maxWidth > averageWidth) {
            variableLength = true;
        }
        return columnWidth;
    }

    @Override
    public double nestTableColumn(Indent indent, double indentation) {
        this.columnHeaderIndentation = indentation;
        return tableColumnWidth();
    }

    @Override
    public void normalizeRowHeight(double normalized) {
        height = normalized;
    }

    @Override
    public OutlineElement outlineElement(String parent, int cardinality,
                                         double labelWidth, double justified,
                                         FocusTraversal parentTraversal) {
        return new OutlineElement(parent, this, cardinality, labelWidth,
                                  justified, parentTraversal);
    }

    @Override
    public double rowHeight(int averageCardinality, double justifiedWidth) {
        return cellHeight(1, justifiedWidth);
    }

    @Override
    public double tableColumnWidth() {
        return columnWidth();
    }

    @Override
    public String toString() {
        return String.format("PrimitiveLayout [%s %s height, width {c: %s, j: %s} ]",
                             node.getField(), height, columnWidth,
                             justifiedWidth);
    }

    @Override
    protected void calculateRootHeight() {
        calculateCellHeight();
    }

    @Override
    protected void clear() {
        super.clear();
        columnHeaderIndentation = 0.0;
    }

    protected double getColumnHeaderWidth() {
        return snap(justifiedWidth + columnHeaderIndentation);
    }

    @Override
    protected Primitive getNode() {
        return (Primitive) node;
    }

    protected double width(JsonNode row) {
        return layout.totalTextWidth(layout.textWidth(LayoutProvider.toString(row)));
    }
}
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

import static com.chiralbehaviors.layout.schema.SchemaNode.asList;
import static com.chiralbehaviors.layout.style.Style.snap;

import java.util.List;
import java.util.function.Function;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.PrimitiveList;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.style.PrimitiveStyle;
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
    protected int                  averageCardinality;
    protected double               maxWidth;
    protected final PrimitiveStyle style;
    private double                 cellHeight;
    @SuppressWarnings("unused")
    private boolean                variableLength;

    public PrimitiveLayout(Primitive p, PrimitiveStyle style) {
        super(p, style.getLabelStyle());
        this.style = style;
    }

    public LayoutCell<?> buildCell(FocusTraversal<?> pt) {
        return style.build(pt, this);
    }

    @Override
    public LayoutCell<? extends Region> buildColumn(double rendered,
                                                    FocusTraversal<?> parentTraversal,
                                                    Style model) {
        LayoutCell<? extends Region> control = buildControl(parentTraversal,
                                                            model);
        control.getNode()
               .setMinSize(justifiedWidth, rendered);
        control.getNode()
               .setPrefSize(justifiedWidth, rendered);
        control.getNode()
               .setMaxSize(justifiedWidth, rendered);
        return control;
    }

    @Override
    public LayoutCell<? extends Region> buildControl(FocusTraversal<?> parentTraversal,
                                                     Style model) {
        return averageCardinality > 1 ? new PrimitiveList(this, parentTraversal)
                                      : buildCell(parentTraversal);
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
        boolean list = resolvedCardinality > 1;
        cellHeight = snap(style.getHeight(maxWidth, justified));
        if (list) {
            height = (cellHeight * resolvedCardinality)
                     + style.getListVerticalInset();
        } else {
            height = cellHeight;
        }
        return height;
    }

    @Override
    public Function<Double, ColumnHeader> columnHeader() {
        return rendered -> new ColumnHeader(Style.snap(justifiedWidth
                                                       + columnHeaderIndentation),
                                            rendered, this);
    }

    @Override
    public double columnWidth() {
        return Math.max(columnWidth, labelWidth);
    }

    @Override
    public void compress(double available) {
        justifiedWidth = Style.snap(available);
    }

    @Override
    public JsonNode extractFrom(JsonNode datum) {
        return node.extractFrom(datum);
    }

    public double getCellHeight() {
        return cellHeight;
    }

    @Override
    public Primitive getNode() {
        return (Primitive) node;
    }

    @Override
    public double justify(double justified) {
        justifiedWidth = Style.snap(justified);
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
    public double measure(JsonNode data, Function<JsonNode, JsonNode> extractor,
                          Style model) {
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
                               Style.snap(Math.max(getNode().getDefaultWidth(),
                                                   averageWidth)));
        if (maxWidth > averageWidth) {
            variableLength = true;
        }
        return columnWidth;
    }

    @Override
    public SchemaNodeLayout measure(JsonNode datum, Style layout) {
        ArrayNode setOf = JsonNodeFactory.instance.arrayNode();
        setOf.add(datum);
        measure(setOf, n -> n, layout);
        return this;
    }

    @Override
    public double nestTableColumn(Indent indent, Insets inset) {
        switch (indent) {
            case LEFT:
                columnHeaderIndentation = inset.getLeft();
                break;
            case NONE:
                break;
            case RIGHT:
                columnHeaderIndentation = inset.getRight();
                break;
            case SINGULAR:
                columnHeaderIndentation = inset.getLeft() + inset.getRight();
                break;
            default:
                throw new IllegalArgumentException(String.format("%s is not a valid primitive indentation",
                                                                 indent));

        }
        return tableColumnWidth();
    }

    @Override
    public void normalizeRowHeight(double normalized) {
        height = normalized;
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
        cellHeight(averageCardinality, justifiedWidth);
    }

    protected double width(JsonNode row) {
        return style.width(row);
    }
}
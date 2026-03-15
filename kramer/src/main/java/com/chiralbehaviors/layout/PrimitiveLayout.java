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
public final class PrimitiveLayout extends SchemaNodeLayout {
    protected int                  averageCardinality;
    protected double               dataWidth;
    private boolean                isVariableLength          = true;
    protected double               maxWidth;
    protected final PrimitiveStyle style;
    private double                 cellHeight;
    private boolean                useVerticalHeader         = false;
    private MeasureResult          measureResult;

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
        return tableColumnWidth();
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
        double floor = style.getMinValueWidth();
        double effective = Math.max(available, floor);
        if (!isVariableLength && maxWidth > 0) {
            double target = maxWidth;
            double snapWidth = style.getOutlineSnapValueWidth();
            if (snapWidth > 0) {
                target = Math.ceil(target / snapWidth) * snapWidth;
            }
            justifiedWidth = Style.snap(Math.min(effective, target));
        } else {
            justifiedWidth = Style.snap(effective);
        }
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
            averageCardinality = (int) Math.round((double) cardSum / data.size());
            averageWidth = summedDataWidth / data.size();
        }

        // Paper §Table 1: IsVariableLength determines width strategy
        if (averageWidth > 0) {
            isVariableLength = (maxWidth / averageWidth > style.getVariableLengthThreshold());
        } else {
            isVariableLength = true; // safe fallback for empty data
        }

        double effectiveWidth = isVariableLength ? averageWidth : maxWidth;
        dataWidth = Style.snap(Math.max(getNode().getDefaultWidth(),
                                        effectiveWidth));
        columnWidth = Math.max(labelWidth, dataWidth);

        measureResult = new MeasureResult(
            labelWidth, columnWidth, dataWidth, maxWidth,
            averageCardinality, isVariableLength,
            0, 0, null, List.of()
        );

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
        columnHeaderIndentation = switch (indent) {
            case LEFT -> inset.getLeft();
            case NONE -> 0.0;
            case RIGHT -> inset.getRight();
            case SINGULAR -> inset.getLeft() + inset.getRight();
            default -> throw new IllegalArgumentException(String.format("%s is not a valid primitive indentation",
                                                                        indent));
        };
        // Paper Table 1: UseVerticalTableHeader — rotate label when column
        // is narrow relative to label text
        double tcw = tableColumnWidth();
        useVerticalHeader = tcw > 0 && labelWidth > tcw * style.getVerticalHeaderThreshold();
        return tcw;
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
        return Math.min(dataWidth, style.getMaxTablePrimitiveWidth());
    }

    @Override
    public String toString() {
        return String.format("PrimitiveLayout [%s %s height, width {c: %s, j: %s} ]",
                             node.getField(), height, columnWidth,
                             justifiedWidth);
    }

    public boolean isVariableLength() {
        return isVariableLength;
    }

    public boolean isUseVerticalHeader() {
        return useVerticalHeader;
    }

    public MeasureResult getMeasureResult() {
        return measureResult;
    }

    public LayoutResult computeLayout(double width) {
        clear();
        return new LayoutResult(
            false,              // useTable — primitives never use table mode
            useVerticalHeader,  // preserved from nestTableColumn if set
            0,                  // tableColumnWidth — not applicable
            columnHeaderIndentation,
            width,              // constrainedColumnWidth
            List.of()
        );
    }

    public CompressResult computeCompress(double available) {
        double floor = style.getMinValueWidth();
        double effective = Math.max(available, floor);
        double justified;
        if (!isVariableLength && maxWidth > 0) {
            double target = maxWidth;
            double snapWidth = style.getOutlineSnapValueWidth();
            if (snapWidth > 0) {
                target = Math.ceil(target / snapWidth) * snapWidth;
            }
            justified = Style.snap(Math.min(effective, target));
        } else {
            justified = Style.snap(effective);
        }
        return new CompressResult(justified, List.of(), 0, List.of());
    }

    public HeightResult computeCellHeight(int cardinality, double justified) {
        int resolved = Math.min(cardinality, averageCardinality);
        boolean list = resolved > 1;
        double cell = snap(style.getHeight(maxWidth, justified));
        double h;
        if (list) {
            h = (cell * resolved) + style.getListVerticalInset();
        } else {
            h = cell;
        }
        return new HeightResult(h, cell, resolved, 0, List.of());
    }

    void setUseVerticalHeader(boolean useVerticalHeader) {
        this.useVerticalHeader = useVerticalHeader;
    }

    @Override
    public double columnHeaderHeight() {
        if (useVerticalHeader) {
            return Style.snap(labelWidth);
        }
        return super.columnHeaderHeight();
    }

    @Override
    protected void calculateRootHeight() {
        cellHeight(averageCardinality, justifiedWidth);
    }

    @Override
    protected void clear() {
        super.clear();
        // isVariableLength must NOT be reset here — it is set in measure()
        // and must survive through layout() into compress() in the
        // autoLayout pipeline: measure → layout → compress.
        // useVerticalHeader MUST reset — it is set in nestTableColumn()
        // which runs AFTER clear() in the layout() pipeline.
        useVerticalHeader = false;
    }

    protected double width(JsonNode row) {
        return style.width(row);
    }
}
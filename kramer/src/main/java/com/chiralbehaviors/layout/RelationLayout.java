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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.outline.Outline;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.Layout;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.table.ColumnHeader;
import com.chiralbehaviors.layout.table.NestedRow;
import com.chiralbehaviors.layout.table.NestedTable;
import com.chiralbehaviors.layout.table.TableHeader;
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
public class RelationLayout extends SchemaNodeLayout {

    public static ArrayNode flatten(Relation fold, JsonNode datum) {
        ArrayNode flattened = JsonNodeFactory.instance.arrayNode();
        if (datum != null) {
            if (datum.isArray()) {
                datum.forEach(item -> {
                    flattened.addAll(SchemaNode.asArray(item.get(fold.getField())));
                });
            } else {
                flattened.addAll(SchemaNode.asArray(datum.get(fold.getField())));
            }
        }
        return flattened;
    }

    protected int                          averageChildCardinality;
    protected final List<SchemaNodeLayout> children         = new ArrayList<>();
    protected double                       columnHeaderHeight;
    protected final List<ColumnSet>        columnSets       = new ArrayList<>();
    protected Function<JsonNode, JsonNode> extractor;
    protected int                          maxCardinality;
    protected int                          resolvedCardinality;
    protected double                       rowHeight        = -1;
    protected final RelationStyle          style;
    protected double                       tableColumnWidth = 0;
    protected boolean                      useTable         = false;

    public RelationLayout(Relation r, RelationStyle style) {
        super(r, style.getLabelStyle());
        assert r != null && style != null;
        this.style = style;
    }

    @Override
    public void adjustHeight(double delta) {
        super.adjustHeight(delta);
        if (useTable) {
            double subDelta = delta / resolvedCardinality;
            if (delta >= 1.0) {
                rowHeight = Layout.snap(rowHeight + subDelta);
                if (subDelta > 1.0) {
                    children.forEach(f -> f.adjustHeight(subDelta));
                }
            }
            return;
        }
        double subDelta = delta / columnSets.size();
        if (subDelta >= 1.0) {
            columnSets.forEach(c -> c.adjustHeight(subDelta));
        }
    }

    public double baseOutlineCellHeight(double cellHeight) {
        return Layout.snap(cellHeight - style.getOutlineCellVerticalInset());
    }

    public double baseRowCellHeight(double extended) {
        return Layout.snap(extended - style.getRowCellVerticalInset()
                           - style.getRowVerticalInset());
    }

    @SuppressWarnings("unchecked")
    @Override
    public LayoutCell<? extends Region> buildColumn(double rendered,
                                                    FocusTraversal<?> parentTraversal,
                                                    Layout model) {
        return new NestedRow(rendered, this, resolvedCardinality,
                             (FocusTraversal<NestedRow>) parentTraversal, model,
                             style);
    }

    public TableHeader buildColumnHeader() {
        return new TableHeader(Layout.snap(justifiedWidth
                                           + style.getRowCellHorizontalInset()),
                               columnHeaderHeight, children);
    }

    @Override
    public LayoutCell<?> buildControl(FocusTraversal<?> parentTraversal,
                                      Layout model) {
        return useTable ? buildNestedTable(parentTraversal, model)
                        : buildOutline(parentTraversal, model);
    }

    public LayoutCell<NestedTable> buildNestedTable(FocusTraversal<?> parentTraversal,
                                                    Layout model) {
        return new NestedTable(resolvedCardinality, this, parentTraversal,
                               model, style);
    }

    public Outline buildOutline(FocusTraversal<?> parentTraversal,
                                Layout model) {
        Outline outline = new Outline(getJustifiedWidth(), columnSets.stream()
                                                                     .mapToDouble(cs -> cs.getHeight()
                                                                                        + style.getSpanVerticalInset())
                                                                     .sum()
                                                           + style.getOutlineCellVerticalInset(),
                                      columnSets, resolvedCardinality, this,
                                      parentTraversal, model, style,
                                      labelWidth);
        double width = justifiedWidth + style.getOutlineCellHorizontalInset()
                       + style.getOutlineHorizontalInset();
        outline.getNode()
               .setMinSize(width, height);
        outline.getNode()
               .setPrefSize(width, height);
        outline.getNode()
               .setMaxSize(width, height);
        return outline;
    }

    @Override
    public void calculateCellHeight() {
        cellHeight(averageChildCardinality, justifiedWidth);
    }

    @Override
    public double calculateLabelWidth() {
        return labelWidth(getLabel());
    }

    @Override
    public double calculateTableColumnWidth() {
        return children.stream()
                       .mapToDouble(c -> c.calculateTableColumnWidth())
                       .sum()
               + style.getRowCellHorizontalInset()
               + style.getRowHorizontalInset();
    }

    @Override
    public double cellHeight(int cardinality, double width) {
        if (height > 0) {
            return height;
        }
        resolvedCardinality = resolveCardinality(cardinality);
        if (!useTable) {
            calculateOutlineHeight();
        } else {
            calculateTableHeight();
        }
        return height;
    }

    @Override
    public Function<Double, ColumnHeader> columnHeader() {
        List<Function<Double, ColumnHeader>> nestedHeaders = children.stream()
                                                                     .map(c -> c.columnHeader())
                                                                     .collect(Collectors.toList());
        return rendered -> {
            double width = Layout.snap(justifiedWidth
                                       + columnHeaderIndentation);
            return new ColumnHeader(width, rendered, this, nestedHeaders);
        };
    }

    @Override
    public double columnHeaderHeight() {
        return super.columnHeaderHeight() + children.stream()
                                                    .mapToDouble(c -> c.columnHeaderHeight())
                                                    .max()
                                                    .orElse(0.0);
    }

    @Override
    public double columnWidth() {
        return Layout.snap(columnWidth + style.getOutlineCellHorizontalInset());
    }

    @Override
    public void compress(double justified) {
        if (useTable) {
            justifyTable(justified);
            return;
        }
        double available = justified - style.getOutlineHorizontalInset()
                - style.getOutlineCellHorizontalInset();
        columnSets.clear();
        justifiedWidth = Layout.snap(available);
        columnSets.clear();
        ColumnSet current = null;
        double halfWidth = Layout.snap((available / 2d)
                                       - style.getColumnHorizontalInset());
        for (SchemaNodeLayout child : children) {
            double childWidth = labelWidth + child.layoutWidth();
            if (childWidth > halfWidth || current == null) {
                current = new ColumnSet();
                columnSets.add(current);
                current.add(child);
                if (childWidth > halfWidth) {
                    current = null;
                }
            } else {
                current.add(child);
            }
        }
        columnSets.forEach(cs -> cs.compress(averageChildCardinality, available,
                                             style, labelWidth));
    }

    @Override
    public JsonNode extractFrom(JsonNode datum) {
        JsonNode extracted = extractor.apply(datum);
        return node.extractFrom(extracted);
    }

    public void forEach(Consumer<? super SchemaNodeLayout> action) {
        children.forEach(action);
    }

    public int getAverageCardinality() {
        return averageChildCardinality;
    }

    public double getColumnHeaderHeight() {
        if (columnHeaderHeight <= 0) {
            columnHeaderHeight = Layout.snap((labelStyle.getHeight())
                                             + children.stream()
                                                       .mapToDouble(c -> c.columnHeaderHeight())
                                                       .max()
                                                       .orElse(0.0));
        }
        return columnHeaderHeight;
    }

    @Override
    public Relation getNode() {
        return (Relation) node;
    }

    public double getRowHeight() {
        return rowHeight;
    }

    public String getStyleClass() {
        return node.getField();
    }

    @Override
    public double justify(double justifed) {
        justifyColumn(Layout.snap(justifed - style.getNestedHorizontalInset()));
        return justifed;
    }

    public double justifyTable(double justifed) {
        justifyColumn(Layout.snap(justifed - style.getRowCellHorizontalInset()
                                  - style.getTableHorizontalInset()));
        return justifed;
    }

    @Override
    public double layout(double width) {
        clear();
        double available = (width - labelWidth)
                           - style.getOutlineCellHorizontalInset()
                           - style.getOutlineCellHorizontalInset();
        assert available > 0;
        columnWidth = children.stream()
                              .mapToDouble(c -> c.layout(available))
                              .max()
                              .orElse(0.0)
                      + labelWidth;
        double tableWidth = calculateTableColumnWidth();
        if (tableWidth <= columnWidth()) {
            return nestTableColumn(Indent.TOP, style.getNestedInsets());
        }
        return columnWidth();
    }

    @Override
    public double layoutWidth() {
        return useTable ? tableColumnWidth() : columnWidth();
    }

    @Override
    public double measure(JsonNode datum,
                          Function<JsonNode, JsonNode> extractor,
                          Layout model) {
        clear();
        children.clear();
        double sum = 0;
        columnWidth = 0;
        int singularChildren = 0;
        maxCardinality = datum.size();

        for (SchemaNode child : getNode().getChildren()) {
            Fold fold = model.layout(child)
                             .fold(datum, extractor, model);
            children.add(fold.getLayout());
            columnWidth = Layout.snap(Math.max(columnWidth, fold.getLayout()
                                                                .measure(fold.datum,
                                                                         n -> n,
                                                                         model)));
            if (fold.averageCardinality == 1) {
                singularChildren++;
            } else {
                sum += fold.averageCardinality;
            }
        }
        int effectiveChildren = children.size() - singularChildren;
        averageChildCardinality = Math.max(1,
                                           Math.min(4,
                                                    effectiveChildren == 0 ? 1
                                                                           : (int) Math.ceil(sum
                                                                                             / effectiveChildren)));

        labelWidth = children.stream()
                             .mapToDouble(child -> child.calculateLabelWidth())
                             .max()
                             .getAsDouble();
        columnWidth = Layout.snap(labelWidth + columnWidth);
        return columnWidth + style.getElementHorizontalInset()
               + style.getColumnHorizontalInset()
               + style.getSpanHorizontalInset()
               + style.getOutlineCellHorizontalInset();
    }

    @Override
    public double nestTableColumn(Indent indent, Insets indentation) {
        useTable = true;
        rowHeight = -1.0;
        columnHeaderHeight = -1.0;
        height = -1.0;
        Insets nestedInsets = style.getNestedInsets();
        columnHeaderIndentation = indentation.getLeft() + indentation.getRight()
                                  + nestedInsets.getLeft()
                                  + nestedInsets.getRight();
        tableColumnWidth = Layout.snap(children.stream()
                                               .mapToDouble(c -> {
                                                   Indent child = indent(indent,
                                                                         c);
                                                   return c.nestTableColumn(child,
                                                                            indent.indent(indentation,
                                                                                          child,
                                                                                          nestedInsets));
                                               })
                                               .sum());
        return tableColumnWidth();
    }

    @Override
    public void normalizeRowHeight(double normalized) {
        double deficit = normalized - height;
        double childDeficit = deficit / resolvedCardinality;
        rowHeight = Layout.snap(rowHeight + childDeficit);
        height = normalized;

        children.forEach(c -> c.normalizeRowHeight(rowHeight));
    }

    public double outlineCellHeight(double baseHeight) {
        return baseHeight + style.getOutlineCellVerticalInset();
    }

    @Override
    public double rowHeight(int cardinality, double justified) {
        resolvedCardinality = resolveCardinality(cardinality);
        rowHeight = calculateRowHeight();
        height = Layout.snap((resolvedCardinality * rowHeight)
                             + style.getRowVerticalInset());
        return height;
    }

    @Override
    public double tableColumnWidth() {
        assert tableColumnWidth > 0.0 : String.format("%s tcw <= 0: %s",
                                                      node.getLabel(),
                                                      tableColumnWidth);
        return Layout.snap(tableColumnWidth + style.getRowCellHorizontalInset()
                           + style.getRowHorizontalInset());
    }

    @Override
    public String toString() {
        return String.format("RelationLayout [%s %s height x %s card, width {o: %s, t: %s, j: %s} ]",
                             node.getField(), height, averageChildCardinality,
                             columnWidth, tableColumnWidth, justifiedWidth);
    }

    protected void calculateOutlineHeight() {
        height = Layout.snap((resolvedCardinality * (columnSets.stream()
                                                               .mapToDouble(cs -> cs.getHeight()
                                                                                  + style.getSpanVerticalInset())
                                                               .sum()
                                                     + style.getOutlineCellVerticalInset())
                              + style.getOutineVerticalInset()));
    }

    @Override
    protected void calculateRootHeight() {
        if (useTable) {
            cellHeight(resolvedCardinality, justifiedWidth);
        }
    }

    protected double calculateRowHeight() {
        double elementHeight = Layout.snap(children.stream()
                                                   .mapToDouble(child -> child.rowHeight(averageChildCardinality,
                                                                                         justifiedWidth))
                                                   .max()
                                                   .getAsDouble());
        children.forEach(c -> c.normalizeRowHeight(elementHeight));
        return Layout.snap(elementHeight + style.getRowCellVerticalInset()
                           + style.getRowVerticalInset());
    }

    protected void calculateTableHeight() {
        columnHeaderHeight = children.stream()
                                     .mapToDouble(c -> c.columnHeaderHeight())
                                     .max()
                                     .orElse(0.0);
        rowHeight = calculateRowHeight();
        height = Layout.snap((resolvedCardinality * rowHeight)
                             + columnHeaderHeight)
                 + style.getRowVerticalInset() + style.getTableVerticalInset();
    }

    @Override
    protected void clear() {
        super.clear();
        useTable = false;
        tableColumnWidth = -1.0;
        columnHeaderHeight = -1.0;
    }

    @Override
    protected Fold fold(JsonNode datum, Function<JsonNode, JsonNode> extractor,
                        Layout model) {

        Relation fold = getNode().getAutoFoldable();
        if (fold != null) {
            ArrayNode flattened = flatten(getNode(), datum);
            return model.layout(fold)
                        .fold(flattened, item -> {
                            JsonNode extracted = extractor.apply(item);
                            ArrayNode flat = flatten(getNode(), extracted);
                            return flat;
                        }, model);
        }
        this.extractor = extractor;

        return fold(datum);
    }

    protected Indent indent(Indent parent, SchemaNodeLayout child) {

        boolean isFirst = isFirst(child);
        boolean isLast = isLast(child);
        if (isFirst && isLast) {
            return Indent.SINGULAR;
        }
        if (isFirst) {
            return Indent.LEFT;
        } else if (isLast) {
            return Indent.RIGHT;
        } else {
            return Indent.NONE;
        }
    }

    protected boolean isFirst(SchemaNodeLayout child) {
        return child.equals(children.get(0));
    }

    protected boolean isLast(SchemaNodeLayout child) {
        return child.equals(children.get(children.size() - 1));
    }

    protected void justifyColumn(double available) {
        double[] remaining = new double[] { available };
        SchemaNodeLayout last = children.get(children.size() - 1);
        justifiedWidth = Layout.snap(available);
        children.forEach(child -> {
            double childJustified = Layout.relax(available
                                                 * (child.tableColumnWidth()
                                                    / tableColumnWidth));

            if (child.equals(last)) {
                childJustified = remaining[0];
            } else {
                remaining[0] -= childJustified;
            }
            child.justify(childJustified);
        });
    }

    protected int resolveCardinality(int cardinality) {
        return Math.max(1, Math.min(cardinality, maxCardinality));
    }

    public double getRowCellHeight() {
        return rowHeight - style.getRowVerticalInset();
    }
}
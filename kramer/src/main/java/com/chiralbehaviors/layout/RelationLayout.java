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

import static com.chiralbehaviors.layout.DefaultStyleProvider.relax;
import static com.chiralbehaviors.layout.DefaultStyleProvider.snap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.chiralbehaviors.layout.StyleProvider.StyledInsets;
import com.chiralbehaviors.layout.cell.FocusTraversal;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.outline.Outline;
import com.chiralbehaviors.layout.outline.OutlineColumn;
import com.chiralbehaviors.layout.outline.OutlineElement;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.table.ColumnHeader;
import com.chiralbehaviors.layout.table.NestedRow;
import com.chiralbehaviors.layout.table.NestedTable;
import com.chiralbehaviors.layout.table.TableHeader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.layout.Region;
import javafx.util.Pair;

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
    protected final StyledInsets           outlineInsets;
    protected int                          resolvedCardinality;
    protected double                       rowHeight        = -1;
    protected double                       tableColumnWidth = 0;
    protected final StyledInsets           tableInsets;
    protected boolean                      useTable         = false;

    public RelationLayout(DefaultStyleProvider layout, Relation r) {
        super(layout, r);
        assert r != null && layout != null;
        Pair<StyledInsets, StyledInsets> insets = layout.insets(this);
        tableInsets = insets.getKey();
        outlineInsets = insets.getValue();
    }

    @Override
    public void adjustHeight(double delta) {
        super.adjustHeight(delta);
        if (useTable) {
            double subDelta = delta / resolvedCardinality;
            if (delta >= 1.0) {
                rowHeight = snap(rowHeight + subDelta);
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

    public <T extends LayoutCell<?>> void apply(VirtualFlow<T> list) {
        layout.getModel()
              .apply(list, getNode());
    }

    public double baseOutlineCellHeight(double cellHeight) {
        return snap(cellHeight - outlineInsets.getCellVerticalInset());
    }

    public double baseRowCellHeight(double extended) {
        return snap(extended - tableInsets.getCellVerticalInset());
    }

    @SuppressWarnings("unchecked")
    @Override
    public LayoutCell<? extends Region> buildColumn(double rendered,
                                                    FocusTraversal<?> parentTraversal) {
        return new NestedRow(rendered, this, resolvedCardinality,
                             (FocusTraversal<NestedRow>) parentTraversal);
    }

    public TableHeader buildColumnHeader() {
        return new TableHeader(snap(justifiedWidth
                                    + tableInsets.getCellHorizontalInset()),
                               columnHeaderHeight, children);
    }

    @Override
    public LayoutCell<?> buildControl(FocusTraversal<?> parentTraversal) {
        return useTable ? buildNestedTable(parentTraversal)
                        : buildOutline(parentTraversal);
    }

    public LayoutCell<NestedTable> buildNestedTable(FocusTraversal<?> parentTraversal) {
        return new NestedTable(resolvedCardinality, this, parentTraversal);
    }

    public Outline buildOutline(FocusTraversal<?> parentTraversal) {
        Outline outline = new Outline(height, columnSets, resolvedCardinality,
                                      this, parentTraversal);
        outline.getNode()
               .setMinWidth(columnWidth());
        outline.getNode()
               .setPrefWidth(columnWidth());
        outline.getNode()
               .setMaxWidth(columnWidth());

        outline.getNode()
               .setMinHeight(height);
        outline.getNode()
               .setPrefHeight(height);
        outline.getNode()
               .setMaxHeight(height);
        return outline;
    }

    @Override
    public void calculateCellHeight() {
        cellHeight(averageChildCardinality, justifiedWidth);
    }

    @Override
    public double calculateTableColumnWidth() {
        return children.stream()
                       .mapToDouble(c -> c.calculateTableColumnWidth())
                       .sum()
               + tableInsets.getCellHorizontalInset();
    }

    @Override
    public double cellHeight(int cardinality, double width) {
        if (height > 0) {
            return height;
        }
        resolvedCardinality = resolveCardinality(cardinality);
        if (!useTable) {
            height = outlineHeight(resolvedCardinality);
        } else {
            calculateTableHeights();
        }
        return height;
    }

    @Override
    public Function<Double, ColumnHeader> columnHeader() {
        List<Function<Double, ColumnHeader>> nestedHeaders = children.stream()
                                                                     .map(c -> c.columnHeader())
                                                                     .collect(Collectors.toList());
        return rendered -> {
            double width = snap(justifiedWidth + columnHeaderIndentation);
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
        return snap(columnWidth + outlineInsets.getCellHorizontalInset());
    }

    @Override
    public void compress(double justified) {
        if (useTable) {
            justify(justified);
            return;
        }
        columnSets.clear();
        justifiedWidth = snap(justified);
        columnSets.clear();
        ColumnSet current = null;
        double available = baseColumnWidth(justified);
        double halfWidth = snap(available / 2d);
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
                                             labelWidth));
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
            columnHeaderHeight = snap((layout.getTextLineHeight()
                                       + layout.getTextVerticalInset())
                                      + children.stream()
                                                .mapToDouble(c -> c.columnHeaderHeight())
                                                .max()
                                                .orElse(0.0));
        }
        return columnHeaderHeight;
    }

    @Override
    public double getJustifiedColumnWidth() {
        return snap(justifiedWidth
                    + (useTable ? tableInsets.getCellHorizontalInset()
                                : outlineInsets.getCellHorizontalInset()));
    }

    public double getRowHeight() {
        return rowHeight;
    }

    public String getStyleClass() {
        return node.getField();
    }

    @Override
    public double justify(double justifed) {
        double available = snap(justifed
                                - tableInsets.getCellHorizontalInset());
        double[] remaining = new double[] { available };
        SchemaNodeLayout last = children.get(children.size() - 1);
        justifiedWidth = snap(children.stream()
                                      .mapToDouble(child -> {
                                          double childJustified = relax(available
                                                                        * (child.tableColumnWidth()
                                                                           / tableColumnWidth));

                                          if (child.equals(last)) {
                                              childJustified = remaining[0];
                                          } else {
                                              remaining[0] -= childJustified;
                                          }
                                          return child.justify(childJustified);
                                      })
                                      .sum());
        return justifed;
    }

    @Override
    public double layout(double width) {
        clear();
        double available = baseColumnWidth(width - labelWidth);
        assert available > 0;
        columnWidth = children.stream()
                              .mapToDouble(c -> c.layout(available))
                              .max()
                              .orElse(0.0)
                      + labelWidth;
        double tableWidth = calculateTableColumnWidth();
        if (tableWidth <= columnWidth()) {
            return nestTableColumn(Indent.TOP, 0);
        }
        return columnWidth();
    }

    @Override
    public double layoutWidth() {
        return useTable ? tableColumnWidth() : columnWidth();
    }

    @Override
    public double measure(JsonNode datum,
                          Function<JsonNode, JsonNode> extractor) {
        clear();
        children.clear();
        double sum = 0;
        columnWidth = 0;
        int singularChildren = 0;
        maxCardinality = datum.size();

        for (SchemaNode child : getNode().getChildren()) {
            Fold fold = layout.layout(child)
                              .fold(datum, extractor);
            children.add(fold.getLayout());
            columnWidth = snap(Math.max(columnWidth, fold.getLayout()
                                                         .measure(fold.datum,
                                                                  n -> n)));
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
        columnWidth = snap(labelWidth + columnWidth);
        return columnWidth();
    }

    @Override
    public double calculateLabelWidth() {
        return labelWidth(getLabel());
    }

    @Override
    public double nestTableColumn(Indent indent, double indentation) {
        columnHeaderIndentation = snap(indentation
                                       + tableInsets.getCellHorizontalInset());
        useTable = true;
        rowHeight = -1.0;
        columnHeaderHeight = -1.0;
        height = -1.0;
        tableColumnWidth = snap(children.stream()
                                        .mapToDouble(c -> {
                                            Indent child = indent(indent, c);
                                            return c.nestTableColumn(child,
                                                                     indent.indent(child,
                                                                                   tableInsets,
                                                                                   indentation));
                                        })
                                        .sum());
        return tableColumnWidth();
    }

    @Override
    public void normalizeRowHeight(double normalized) {
        double deficit = normalized - height;
        double childDeficit = deficit / (double) resolvedCardinality;
        rowHeight = snap(rowHeight + childDeficit);
        height = normalized;

        children.forEach(c -> c.normalizeRowHeight(rowHeight));
    }

    public double outlineCellHeight(double baseHeight) {
        return baseHeight + outlineInsets.getCellVerticalInset();
    }

    @Override
    public OutlineElement outlineElement(String parent, int cardinality,
                                         double labelWidth, double justified,
                                         FocusTraversal<OutlineColumn> parentTraversal) {

        return new OutlineElement(parent, this, cardinality, labelWidth,
                                  justified, parentTraversal);
    }

    @Override
    public double rowHeight(int cardinality, double justified) {
        resolvedCardinality = resolveCardinality(cardinality);
        rowHeight = calculateRowHeight();
        height = snap((resolvedCardinality * rowHeight)
                      + tableInsets.getVerticalInset());
        return height;
    }

    @Override
    public double tableColumnWidth() {
        assert tableColumnWidth > 0.0 : String.format("%s tcw <= 0: %s",
                                                      node.getLabel(),
                                                      tableColumnWidth);
        return snap(tableColumnWidth + tableInsets.getCellHorizontalInset());
    }

    @Override
    public String toString() {
        return String.format("RelationLayout [%s %s height x %s card, width {o: %s, t: %s, j: %s} ]",
                             node.getField(), height, averageChildCardinality,
                             columnWidth, tableColumnWidth, justifiedWidth);
    }

    protected double baseColumnWidth(double width) {
        return snap(width - outlineInsets.getCellHorizontalInset());
    }

    @Override
    protected void calculateRootHeight() {
        if (useTable) {
            cellHeight(maxCardinality, justifiedWidth);
        }
    }

    protected double calculateRowHeight() {
        double elementHeight = snap(children.stream()
                                            .mapToDouble(child -> child.rowHeight(averageChildCardinality,
                                                                                  justifiedWidth))
                                            .max()
                                            .getAsDouble());
        children.forEach(c -> c.normalizeRowHeight(elementHeight));
        return snap(elementHeight + tableInsets.getCellVerticalInset());
    }

    @Override
    protected void clear() {
        super.clear();
        useTable = false;
        tableColumnWidth = -1.0;
    }

    protected double elementHeight() {
        return snap(children.stream()
                            .mapToDouble(child -> child.rowHeight(averageChildCardinality,
                                                                  justifiedWidth))
                            .max()
                            .getAsDouble());
    }

    @Override
    protected Fold fold(JsonNode datum,
                        Function<JsonNode, JsonNode> extractor) {

        Relation fold = getNode().getAutoFoldable();
        if (fold != null) {
            ArrayNode flattened = flatten(getNode(), datum);
            return layout.layout(fold)
                         .fold(flattened, item -> {
                             JsonNode extracted = extractor.apply(item);
                             ArrayNode flat = flatten(getNode(), extracted);
                             return flat;
                         });
        }
        this.extractor = extractor;

        return fold(datum);
    }

    @Override
    protected Relation getNode() {
        return (Relation) node;
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

    protected double outlineHeight(int cardinality) {
        return outlineHeight(cardinality, columnSets.stream()
                                                    .mapToDouble(cs -> cs.getCellHeight())
                                                    .sum());
    }

    protected double outlineHeight(int cardinality, double elementHeight) {
        return snap((cardinality
                     * (elementHeight + outlineInsets.getCellVerticalInset()))
                    + outlineInsets.getVerticalInset());
    }

    protected int resolveCardinality(int cardinality) {
        return Math.max(1, Math.min(cardinality, maxCardinality));
    }

    private void calculateTableHeights() {
        columnHeaderHeight = children.stream()
                                     .mapToDouble(c -> c.columnHeaderHeight())
                                     .max()
                                     .orElse(0.0);
        rowHeight = calculateRowHeight();
        height = snap((resolvedCardinality * rowHeight)
                      + tableInsets.getVerticalInset() + columnHeaderHeight);
    }
}
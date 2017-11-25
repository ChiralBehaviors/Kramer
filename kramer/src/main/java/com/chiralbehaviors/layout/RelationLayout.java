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

import static com.chiralbehaviors.layout.LayoutProvider.relax;
import static com.chiralbehaviors.layout.LayoutProvider.snap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.flowless.Cell;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.outline.Outline;
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

import javafx.scene.control.Control;
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

    protected int                          averageCardinality;
    protected final List<SchemaNodeLayout> children         = new ArrayList<>();
    protected double                       columnHeaderHeight;
    protected final List<ColumnSet>        columnSets       = new ArrayList<>();
    protected Function<JsonNode, JsonNode> extractor;
    protected int                          maxCardinality;
    protected final Relation               r;
    protected int                          resolvedCardinality;
    protected double                       rowHeight        = -1;
    protected double                       tableColumnWidth = 0;
    protected boolean                      useTable         = false;

    public RelationLayout(LayoutProvider layout, Relation r) {
        super(layout);
        assert r != null && layout != null;
        this.r = r;
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

    public void apply(VirtualFlow<JsonNode, Cell<JsonNode, ?>> list) {
        layout.getModel()
              .apply(list, r);
    }

    public double baseOutlineCellHeight(double cellHeight) {
        return snap(cellHeight - layout.getCellVerticalInset());
    }

    public double baseRowCellHeight(double extended) {
        return snap(extended - layout.getCellVerticalInset());
    }

    @Override
    public LayoutCell<? extends Region> buildColumn(double rendered) {
        return new NestedRow(rendered, this, resolvedCardinality);
    }

    public TableHeader buildColumnHeader() {
        return new TableHeader(snap(justifiedWidth
                                    + layout.getNestedCellInset()),
                               columnHeaderHeight, children);
    }

    @Override
    public LayoutCell<?> buildControl() {
        return useTable ? buildNestedTable() : buildOutline();
    }

    public LayoutCell<NestedTable> buildNestedTable() {
        return new NestedTable(resolvedCardinality, this);
    }

    public Outline buildOutline() {
        Outline outline = new Outline(height, columnSets, resolvedCardinality,
                                      this);
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
    public double calculateTableColumnWidth() {
        return children.stream()
                       .mapToDouble(c -> c.calculateTableColumnWidth())
                       .sum()
               + layout.getNestedInset();
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
            columnHeaderHeight = children.stream()
                                         .mapToDouble(c -> c.columnHeaderHeight())
                                         .max()
                                         .orElse(0.0);
            double elementHeight = elementHeight();
            rowHeight = rowHeight(elementHeight);
            height = snap((resolvedCardinality
                           * snap(elementHeight
                                  + layout.getCellVerticalInset()))
                          + layout.getVerticalInset() + columnHeaderHeight);
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
        return snap(columnWidth + layout.getNestedInset());
    }

    @Override
    public void compress(double justified) {
        if (useTable) {
            justify(justified);
            return;
        }
        columnSets.clear();
        justifiedWidth = snap(baseColumnWidth(justified));
        columnSets.clear();
        ColumnSet current = null;
        double halfWidth = snap(justifiedWidth / 2d);
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
        columnSets.forEach(cs -> cs.compress(averageCardinality, justifiedWidth,
                                             labelWidth));
    }

    @Override
    public JsonNode extractFrom(JsonNode node) {
        JsonNode extracted = extractor.apply(node);
        return r.extractFrom(extracted);
    }

    public void forEach(Consumer<? super SchemaNodeLayout> action) {
        children.forEach(action);
    }

    public int getAverageCardinality() {
        return averageCardinality;
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
    public String getField() {
        return r.getField();
    }

    public double getJustifiedCellWidth() {
        return snap(justifiedWidth + layout.getCellHorizontalInset());
    }

    @Override
    public double getJustifiedColumnWidth() {
        return snap(justifiedWidth + layout.getNestedInset());
    }

    @Override
    public String getLabel() {
        return r.getLabel();
    }

    public double getRowHeight() {
        return rowHeight;
    }

    public String getStyleClass() {
        return r.getField();
    }

    @Override
    public double justify(double justifed) {
        double available = snap(justifed - layout.getNestedCellInset());
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
    public Control label(double labelWidth) {
        return label(labelWidth, r.getLabel());
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

        for (SchemaNode child : r.getChildren()) {
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
        averageCardinality = Math.max(1,
                                      Math.min(4,
                                               effectiveChildren == 0 ? 1
                                                                      : (int) Math.ceil(sum
                                                                                        / effectiveChildren)));

        labelWidth = children.stream()
                             .mapToDouble(child -> child.getLabelWidth())
                             .max()
                             .getAsDouble();
        columnWidth = snap(labelWidth + columnWidth);
        return columnWidth();
    }

    @Override
    public double nestTableColumn(Indent indent, double indentation) {
        columnHeaderIndentation = snap(indentation
                                       + layout.getNestedCellInset());
        useTable = true;
        rowHeight = -1.0;
        columnHeaderHeight = -1.0;
        height = -1.0;
        tableColumnWidth = snap(children.stream()
                                        .mapToDouble(c -> {
                                            Indent child = indent(indent, c);
                                            return c.nestTableColumn(child,
                                                                     indent.indent(child,
                                                                                   layout,
                                                                                   indentation));
                                        })
                                        .sum());
        return tableColumnWidth();
    }

    public double outlineCellHeight(double baseHeight) {
        return baseHeight + layout.getCellVerticalInset();
    }

    @Override
    public OutlineElement outlineElement(int cardinality, double labelWidth,
                                         double justified) {

        return new OutlineElement(this, cardinality, labelWidth, justified);
    }

    @Override
    public double rowHeight(int cardinality, double justified) {
        resolvedCardinality = resolveCardinality(cardinality);
        rowHeight = rowHeight(elementHeight());
        height = snap((cardinality * rowHeight) + layout.getVerticalInset());
        return height;
    }

    @Override
    public double tableColumnWidth() {
        assert tableColumnWidth > 0.0 : String.format("%s tcw <= 0: %s",
                                                      r.getLabel(),
                                                      tableColumnWidth);
        return snap(tableColumnWidth + layout.getNestedInset());
    }

    @Override
    public String toString() {
        return String.format("RelationLayout [%s %s x %s, {%s, %s, %s} ]",
                             r.getField(), height, averageCardinality,
                             columnWidth, tableColumnWidth, justifiedWidth);
    }

    protected double baseColumnWidth(double width) {
        return snap(width - layout.getNestedInset());
    }

    @Override
    protected void clear() {
        super.clear();
        useTable = false;
        tableColumnWidth = -1.0;
    }

    protected double elementHeight() {
        return snap(children.stream()
                            .mapToDouble(child -> child.rowHeight(averageCardinality,
                                                                  justifiedWidth))
                            .max()
                            .getAsDouble());
    }

    @Override
    protected Fold fold(JsonNode datum,
                        Function<JsonNode, JsonNode> extractor) {

        Relation fold = r.getAutoFoldable();
        if (fold != null) {
            ArrayNode flattened = flatten(r, datum);
            return layout.layout(fold)
                         .fold(flattened, item -> {
                             JsonNode extracted = extractor.apply(item);
                             ArrayNode flat = flatten(r, extracted);
                             return flat;
                         });
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

    protected double outlineHeight(int cardinality) {
        return outlineHeight(cardinality, columnSets.stream()
                                                    .mapToDouble(cs -> cs.getCellHeight())
                                                    .sum());
    }

    protected double outlineHeight(int cardinality, double elementHeight) {
        return snap((cardinality
                     * (elementHeight + layout.getCellVerticalInset()))
                    + layout.getVerticalInset());
    }

    protected int resolveCardinality(int cardinality) {
        return Math.min(cardinality, maxCardinality);
    }

    protected double rowHeight(double elementHeight) {
        return snap(elementHeight + layout.getCellVerticalInset());
    }

}
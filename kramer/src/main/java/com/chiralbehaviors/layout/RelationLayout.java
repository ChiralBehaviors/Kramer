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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.chiralbehaviors.layout.control.JsonControl;
import com.chiralbehaviors.layout.control.NestedTable;
import com.chiralbehaviors.layout.control.Outline;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

/**
 *
 * @author halhildebrand
 *
 */
public class RelationLayout extends SchemaNodeLayout {
    private int                   averageCardinality;
    private double                columnHeaderHeight;
    private final List<ColumnSet> columnSets       = new ArrayList<>();
    private double                labelWidth;
    private int                   maxCardinality;
    private double                outlineWidth     = 0;
    private final Relation        r;
    private double                rowHeight        = -1;
    private double                scroll           = 0.0;
    boolean                       useTable         = false;
    private boolean               singular;
    private double                tableColumnWidth = 0;

    public RelationLayout(LayoutProvider layout, Relation r) {
        super(layout);
        this.r = r;
    }

    @Override
    public void adjustHeight(double delta) {
        super.adjustHeight(delta);
        if (useTable) {
            List<SchemaNode> children = r.getChildren();
            double subDelta = delta / children.size();
            if (delta >= 1.0) {
                children.forEach(f -> f.adjustHeight(subDelta));
            }
            return;
        }
        double subDelta = delta / columnSets.size();
        if (subDelta >= 1.0) {
            columnSets.forEach(c -> c.adjustHeight(subDelta));
        }
    }

    public void apply(ListCell<JsonNode> cell) {
        layout.getModel()
              .apply(cell, r);
    }

    public void apply(ListView<JsonNode> list) {
        layout.getModel()
              .apply(list, r);
    }

    public double baseOutlineCellHeight(double cellHeight) {
        return cellHeight - layout.getListCellVerticalInset();
    }

    public double baseRowCellHeight(double extended) {
        return extended - layout.getListCellVerticalInset();
    }

    public Region buildColumnHeader() {
        HBox header = new HBox();
        List<SchemaNode> children = r.getChildren();
        children.forEach(c -> header.getChildren()
                                    .add(c.buildColumnHeader()
                                          .apply(columnHeaderHeight)));
        return header;
    }

    public JsonControl buildNestedTable(int cardinality) {
        return new NestedTable(this).build(resolveCardinality(cardinality),
                                           this);
    }

    public JsonControl buildOutline(Function<JsonNode, JsonNode> extractor,
                                    int cardinality) {
        return new Outline(this).build(height, columnSets, extractor,
                                       resolveCardinality(cardinality), this);
    }

    public double calculateTableColumnWidth() {
        return r.getChildren()
                .stream()
                .mapToDouble(c -> c.calculateTableColumnWidth())
                .sum()
               + layout.getNestedInset();
    }

    public double cellHeight(int card, double width) {
        if (height > 0) {
            return height;
        }
        int cardinality = resolveCardinality(card);
        if (!useTable) {
            height = outlineHeight(cardinality);
        } else {
            columnHeaderHeight = r.getChildren()
                                  .stream()
                                  .mapToDouble(c -> c.columnHeaderHeight())
                                  .max()
                                  .orElse(0.0);
            double elementHeight = elementHeight();
            rowHeight = rowHeight(elementHeight);
            height = (cardinality
                      * (elementHeight + layout.getListCellVerticalInset()))
                     + layout.getListVerticalInset() + columnHeaderHeight;
        }
        return height;
    }

    public Function<Double, Region> columnHeader() {
        List<SchemaNode> children = r.getChildren();
        List<Function<Double, Region>> nestedHeaders = children.stream()
                                                               .map(c -> c.buildColumnHeader())
                                                               .collect(Collectors.toList());
        return rendered -> {
            VBox columnHeader = new VBox();
            HBox nested = new HBox();

            columnHeader.setMinSize(justifiedWidth, rendered);
            columnHeader.setMaxSize(justifiedWidth, rendered);
            double half = LayoutProvider.snap(rendered / 2.0);
            columnHeader.getChildren()
                        .add(layout.label(justifiedWidth, r.getLabel(), half));
            columnHeader.getChildren()
                        .add(nested);

            nestedHeaders.forEach(n -> {
                nested.getChildren()
                      .add(n.apply(half));
            });
            return columnHeader;
        };
    }

    @Override
    public void compress(double justified, boolean ignored) {
        if (useTable) {
            r.justify(justified - layout.getListHorizontalInset());
            return;
        }
        columnSets.clear();
        justifiedWidth = baseOutlineWidth(justified);
        List<SchemaNode> children = r.getChildren();
        columnSets.clear();
        ColumnSet current = null;
        double halfWidth = snap(justifiedWidth / 2d);
        for (SchemaNode child : children) {
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
                                             labelWidth, scroll > 0.0));
    }

    @Override
    public JsonNode extractFrom(JsonNode node) {
        return r.extractFrom(node);
    }

    public void forEach(Consumer<? super SchemaNode> action) {
        List<SchemaNode> children = r.getChildren();
        children.forEach(c -> action.accept(c));
    }

    public int getAverageCardinality() {
        return averageCardinality;
    }

    public double getColumnHeaderHeight() {
        if (columnHeaderHeight <= 0) {
            columnHeaderHeight = (layout.getTextLineHeight()
                                  + layout.getTextVerticalInset())
                                 + r.getChildren()
                                    .stream()
                                    .mapToDouble(c -> c.columnHeaderHeight())
                                    .max()
                                    .orElse(0.0);
        }
        return columnHeaderHeight;
    }

    @Override
    public double getJustifiedTableColumnWidth() {
        return justifiedWidth + layout.getNestedInset();
    }

    public double getLabelWidth() {
        return labelWidth;
    }

    public double getRowCellWidth() {
        return justifiedWidth - layout.getNestedInset();
    }

    public double getRowHeight() {
        return rowHeight;
    }

    public String getStyleClass() {
        return r.getField();
    }

    public boolean isSingular() {
        return singular;
    }

    @Override
    public double justify(double width) {
        assert width >= tableColumnWidth : String.format("%s justified width incorrect %s < %s",
                                                         r.getLabel(), width,
                                                         tableColumnWidth);
        double slack = Math.max(0, width - tableColumnWidth);
        List<SchemaNode> children = r.getChildren();
        justifiedWidth = snap(children.stream()
                                      .mapToDouble(child -> {
                                          double childWidth = child.tableColumnWidth();
                                          double additional = slack
                                                              * (childWidth
                                                                 / tableColumnWidth);
                                          double childJustified = snap(additional
                                                                       + childWidth);
                                          return child.justify(childJustified);
                                      })
                                      .sum());
        return justifiedWidth;
    }

    @Override
    public double layout(double width) {
        clear();
        double available = snap(width - labelWidth);
        assert available > 0;
        outlineWidth = r.getChildren()
                        .stream()
                        .mapToDouble(c -> c.layout(available))
                        .max()
                        .orElse(0.0)
                       + labelWidth;
        double tableWidth = calculateTableWidth();
        if (tableWidth <= outlineWidth) {
            return nestTable();
        }
        return outlineWidth(outlineWidth);
    }

    @Override
    public double layoutWidth() {
        return useTable ? getTableWidth() : outlineWidth(outlineWidth);
    }

    @Override
    public double measure(JsonNode datum, boolean isSingular) {
        clear();
        double sum = 0;
        outlineWidth = 0;
        singular = isSingular;
        int singularChildren = 0;
        maxCardinality = 0;

        List<SchemaNode> children = r.getChildren();
        for (SchemaNode child : children) {
            ArrayNode aggregate = JsonNodeFactory.instance.arrayNode();
            int cardSum = 0;
            boolean childSingular = false;
            List<JsonNode> data = datum.isArray() ? new ArrayList<>(datum.size())
                                                  : Arrays.asList(datum);
            if (datum.isArray()) {
                datum.forEach(n -> data.add(n));
            }
            for (JsonNode node : data) {
                JsonNode sub = node.get(child.getField());
                if (sub instanceof ArrayNode) {
                    childSingular = false;
                    aggregate.addAll((ArrayNode) sub);
                    cardSum += sub.size();
                } else {
                    childSingular = true;
                    aggregate.add(sub);
                }
            }
            if (childSingular) {
                singularChildren += 1;
            } else {
                sum += data.size() == 0 ? 1 : Math.round(cardSum / data.size());
            }
            outlineWidth = snap(Math.max(outlineWidth,
                                         child.measure(aggregate, childSingular,
                                                       layout)));
            maxCardinality = Math.max(maxCardinality, data.size());
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
        outlineWidth = snap(labelWidth + outlineWidth);
        maxCardinality = Math.max(1, maxCardinality);
        if (!singular && maxCardinality > averageCardinality) {
            scroll = layout.getScrollWidth();
        }
        return r.outlineWidth();
    }

    public double nestTable() {
        tableColumnWidth = nestTableColumn(Indent.TOP, 0);
        return tableColumnWidth;
    }

    public double nestTableColumn(Indent indent, double indentation) {
        useTable = true;
        rowHeight = -1.0;
        columnHeaderHeight = -1.0;
        height = -1.0;
        tableColumnWidth = snap(r.getChildren()
                                 .stream()
                                 .mapToDouble(c -> {
                                     Indent child = indent(indent, c);
                                     return c.nestTableColumn(child,
                                                              indent.indent(child,
                                                                            layout,
                                                                            indentation));
                                 })
                                 .sum());
        return tableColumnWidth;
    }

    public double outlineCellHeight(double baseHeight) {
        return baseHeight + layout.getListCellVerticalInset();
    }

    public Pair<Consumer<JsonNode>, Parent> outlineElement(int cardinality,
                                                           double labelWidth,
                                                           Function<JsonNode, JsonNode> extractor,
                                                           double justified) {

        double available = justified - labelWidth;

        JsonControl control = r.buildControl(cardinality, extractor);

        Control labelControl = label(labelWidth, r.getLabel());
        labelControl.setMinWidth(labelWidth);
        control.setPrefWidth(available);
        control.setMinHeight(height);

        Pane box = new HBox();
        box.getStyleClass()
           .add(r.getField());
        box.setPrefWidth(justified);
        box.setPrefHeight(height);
        box.getChildren()
           .add(labelControl);
        box.getChildren()
           .add(control);

        return new Pair<>(item -> {
            if (item == null) {
                return;
            }
            control.setItem(extractor.apply(item) == null ? null
                                                          : extractor.apply(item)
                                                                     .get(r.getField()));
        }, box);
    }

    public double outlineWidth() {
        return outlineWidth(outlineWidth);
    }

    public int resolvedCardinality() {
        return resolveCardinality(averageCardinality);
    }

    public double rowHeight(int cardinality, double justified) {
        rowHeight = rowHeight(elementHeight());
        height = (cardinality * rowHeight) + layout.getListVerticalInset();
        return height;
    }

    public double tableColumnWidth() {
        assert tableColumnWidth > 0.0;
        return tableColumnWidth;
    }

    @Override
    public String toString() {
        return String.format("RelationLayout [r=%s x %s:%s, {%s, %s, %s} ]",
                             r.getField(), averageCardinality, height,
                             outlineWidth, tableColumnWidth, justifiedWidth);
    }

    protected double baseOutlineWidth(double width) {
        return snap(baseTableWidth(width));
    }

    protected double baseTableWidth(double width) {
        return width - layout.getNestedInset();
    }

    protected double calculateTableWidth() {
        return calculateTableColumnWidth();
    }

    @Override
    protected void clear() {
        super.clear();
        tableColumnWidth = -1.0;
        scroll = 0.0;
    }

    protected double elementHeight() {
        return r.getChildren()
                .stream()
                .mapToDouble(child -> child.rowHeight(averageCardinality,
                                                      justifiedWidth))
                .max()
                .getAsDouble();
    }

    protected double getTableWidth() {
        assert tableColumnWidth > 0.0;
        return tableColumnWidth;
    }

    protected Indent indent(Indent parent, SchemaNode child) {
        List<SchemaNode> children = r.getChildren();

        boolean isFirst = isFirst(child, children);
        boolean isLast = isLast(child, children);
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

    protected boolean isFirst(SchemaNode child, List<SchemaNode> children) {
        return child.equals(children.get(0));
    }

    protected boolean isLast(SchemaNode child, List<SchemaNode> children) {
        return child.equals(children.get(children.size() - 1));
    }

    protected double outlineHeight(int cardinality) {
        return outlineHeight(cardinality, columnSets.stream()
                                                    .mapToDouble(cs -> cs.getCellHeight())
                                                    .sum());
    }

    protected double outlineHeight(int cardinality, double elementHeight) {
        return (cardinality
                * (elementHeight + layout.getListCellVerticalInset()))
               + layout.getListVerticalInset();
    }

    protected double outlineWidth(double outlineWidth) {
        return outlineWidth + layout.getNestedInset();
    }

    protected int resolveCardinality(int cardinality) {
        return singular ? 1 : Math.min(cardinality, maxCardinality);
    }

    protected double rowHeight(double elementHeight) {
        return elementHeight + layout.getListCellVerticalInset();
    }

    public double getJustifiedTableWidth() {
        return justifiedWidth - layout.getListHorizontalInset();
    }
 
    public JsonControl buildControl(int cardinality,
                                    Function<JsonNode, JsonNode> extractor) {
        return useTable ? r.buildNestedTable(extractor, cardinality)
                        : r.buildOutline(extractor, cardinality);
    }
}
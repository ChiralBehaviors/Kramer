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

    private boolean               singular;
    private double                tableColumnWidth = 0;

    public RelationLayout(LayoutProvider layout, Relation r) {
        super(layout);
        this.r = r;
    }

    @Override
    public void adjustHeight(double delta) {
        if (r.isFold()) {
            r.getFold()
             .adjustHeight(delta);
            return;
        }
        super.adjustHeight(delta);
        if (r.isUseTable()) {
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

    @Override
    public double baseTableColumnWidth(double width) {
        return width - indentation(indent);
    }

    public Region buildColumnHeader() {
        HBox header = new HBox();
        r.getChildren()
         .forEach(c -> header.getChildren()
                             .add(c.buildColumnHeader()
                                   .apply(columnHeaderHeight)));
        double width = getTableWidth();
        header.setMinSize(width, columnHeaderHeight);
        header.setMaxSize(width, columnHeaderHeight);
        return header;
    }

    public JsonControl buildNestedTable(int cardinality) {
        return new NestedTable(this).build(singular ? 1
                                                    : Math.min(cardinality,
                                                               maxCardinality),
                                           this);
    }

    public JsonControl buildOutline(Function<JsonNode, JsonNode> extractor,
                                    int cardinality) {
        return new Outline(this).build(height, columnSets, extractor,
                                       singular ? 1
                                                : Math.min(cardinality,
                                                           maxCardinality),
                                       this);
    }

    public double cellHeight(int card, double width) {
        if (height > 0) {
            return height;
        }
        int cardinality = singular ? 1 : Math.min(maxCardinality, card);
        if (!r.isUseTable()) {
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
        List<Function<Double, Region>> nestedHeaders = r.getChildren()
                                                        .stream()
                                                        .map(c -> c.buildColumnHeader())
                                                        .collect(Collectors.toList());
        return rendered -> {
            VBox columnHeader = new VBox();
            HBox nested = new HBox();

            double width = tableColumnWidth(justifiedWidth);
            columnHeader.setMinSize(width, rendered);
            columnHeader.setMaxSize(width, rendered);
            double half = LayoutProvider.snap(rendered / 2.0);
            columnHeader.getChildren()
                        .add(layout.label(width,
                                          r.getLabel(), half));
            columnHeader.getChildren()
                        .add(nested);

            nestedHeaders.forEach(n -> {
                nested.getChildren()
                      .add(n.apply(half));
            });
            return columnHeader;
        };
    }

    public double compress(double justified) {
        if (r.isUseTable()) {
            return r.justifyTable(justified);
        }
        columnSets.clear();
        justifiedWidth = baseOutlineWidth(justified);
        List<SchemaNode> children = r.getChildren();
        double labelWidth = LayoutProvider.snap(children.stream()
                                                        .mapToDouble(n -> n.getLabelWidth())
                                                        .max()
                                                        .orElse(0));
        columnSets.clear();
        ColumnSet current = null;
        double halfWidth = justifiedWidth / 2d;
        for (SchemaNode child : children) {
            double childWidth = labelWidth + child.layoutWidth();
            if (childWidth > halfWidth || current == null) {
                current = new ColumnSet(labelWidth);
                columnSets.add(current);
                current.add(child);
                if (childWidth > halfWidth) {
                    current = null;
                }
            } else {
                current.add(child);
            }
        }
        columnSets.forEach(cs -> cs.compress(averageCardinality,
                                             justifiedWidth));
        return justifiedWidth;
    }

    public JsonNode extractFrom(JsonNode node) {
        return r.extractFrom(node);
    }

    public void forEach(Consumer<? super SchemaNode> action) {
        r.getChildren()
         .forEach(action);
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

    public double getLayoutWidth() {
        return r.isUseTable() ? tableColumnWidth + indentation(indent)
                              : outlineWidth(outlineWidth);
    }

    public double getRowHeight() {
        return rowHeight;
    }

    public String getStyleClass() {
        return r.getField();
    }

    public double getTableColumnWidth() {
        return tableColumnWidth;
    }

    public double getTableWidth() {
        return justifiedWidth + layout.getNestedInset();
    }

    public boolean isSingular() {
        return singular;
    }

    public double justify(double width) {
        justifiedWidth = baseTableColumnWidth(width);
        return justified();
    }

    public double justifyTable(double width) {
        justifiedWidth = width - layout.getNestedInset();
        return justified();
    }

    @Override
    public double labelWidth(String label) {
        return layout.labelWidth(label);
    }

    @Override
    public double layout(double width) {
        clear();
        double available = width - labelWidth;
        double extended = r.getChildren()
                           .stream()
                           .mapToDouble(c -> c.layout(available))
                           .max()
                           .orElse(0.0)
                          + labelWidth;
        double tableWidth = tableWidth();
        if (tableWidth <= extended) {
            r.nestTable();
            return tableWidth;
        }
        return extended;
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
            outlineWidth = Math.max(outlineWidth,
                                    child.measure(aggregate, childSingular,
                                                  layout));
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
        outlineWidth = LayoutProvider.snap(labelWidth + outlineWidth);
        maxCardinality = Math.max(1, maxCardinality);
        return r.isFold() ? r.getFold()
                             .outlineWidth()
                          : r.outlineWidth();
    }

    public double outlineCellHeight(double baseHeight) {
        return baseHeight + layout.getListCellVerticalInset();
    }

    public Pair<Consumer<JsonNode>, Parent> outlineElement(String field,
                                                           int cardinality,
                                                           String label,
                                                           double labelWidth,
                                                           Function<JsonNode, JsonNode> extractor,
                                                           boolean useTable,
                                                           double justified) {

        double available = justified - labelWidth;

        JsonControl control = r.buildControl(cardinality, extractor);

        Control labelControl = label(labelWidth, label);
        labelControl.setMinWidth(labelWidth);
        control.setPrefWidth(available);
        control.setMinHeight(height);

        Pane box = new HBox();
        box.getStyleClass()
           .add(field);
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
                                                                     .get(field));
        }, box);
    }

    public double outlineWidth() {
        return outlineWidth(outlineWidth);
    }

    public double outlineWidth(double outlineWidth) {
        return outlineWidth + layout.getNestedInset();
    }

    public double rowHeight(int cardinality, double justified) {
        double elementHeight = elementHeight();
        rowHeight = rowHeight(elementHeight);
        height = (cardinality
                  * (elementHeight + layout.getListCellVerticalInset()))
                 + layout.getListVerticalInset();
        return height;
    }

    public double tableColumnWidth() {
        return tableColumnWidth(tableColumnWidth);
    }

    @Override
    public double tableColumnWidth(double width) {
        return width + indentation(indent);
    }

    public double tableColumnWidth(INDENT i) {
        rowHeight = -1.0;
        columnHeaderHeight = -1.0;
        height = -1.0;
        indent = i;
        return tableColumnWidth = r.getChildren()
                                   .stream()
                                   .mapToDouble(c -> c.tableColumnWidth(indent(indent,
                                                                               c)))
                                   .sum()
                                  + indentation(indent);
    }

    @Override
    public String toString() {
        return String.format("RelationLayout [r=%s x %s:%s, {%s, %s, %s} ]",
                             r.getField(), averageCardinality, height,
                             outlineWidth, tableColumnWidth, justifiedWidth);
    }

    @Override
    protected double baseOutlineWidth(double width) {
        return LayoutProvider.snap(width - layout.getNestedInset());
    }

    protected double elementHeight() {
        return r.getChildren()
                .stream()
                .mapToDouble(child -> child.rowHeight(averageCardinality,
                                                      justifiedWidth))
                .max()
                .getAsDouble();
    }

    protected INDENT indent(INDENT parent, SchemaNode child) {
        List<SchemaNode> children = r.getChildren();
        if (children.size() == 1) {
            if (parent.equals(INDENT.RIGHT)) {
                return INDENT.SINGULAR_RIGHT;
            }
            return INDENT.SINGULAR;
        }
        if (child.equals(children.get(0)))
            return INDENT.LEFT;
        if (child.equals(children.get(children.size() - 1)))
            return INDENT.RIGHT;
        return INDENT.NONE;
    }

    protected double justified() {
        double slack = LayoutProvider.snap(Math.max(0, justifiedWidth
                                                       - tableColumnWidth));
        List<SchemaNode> children = r.getChildren();
        double total = LayoutProvider.snap(children.stream()
                                                   .map(child -> child.tableColumnWidth())
                                                   .reduce((a, b) -> a + b)
                                                   .orElse(0.0d));
        children.forEach(child -> {
            double childWidth = child.tableColumnWidth();
            double additional = LayoutProvider.snap(slack
                                                    * (childWidth / total));
            double childJustified = additional + childWidth;
            child.justify(childJustified);
        });
        return justifiedWidth;
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

    protected double rowHeight(double elementHeight) {
        return elementHeight + layout.getListCellVerticalInset();
    }

    protected double tableWidth() {
        rowHeight = -1.0;
        columnHeaderHeight = -1.0;
        indent = INDENT.TOP;
        tableColumnWidth = r.getChildren()
                            .stream()
                            .mapToDouble(c -> c.tableColumnWidth(indent(indent,
                                                                        c)))
                            .sum();
        return tableColumnWidth + layout.getNestedInset();
    }

}
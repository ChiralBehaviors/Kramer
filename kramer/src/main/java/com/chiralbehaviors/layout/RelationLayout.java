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
import javafx.util.Pair;

/**
 *
 * @author halhildebrand
 *
 */
public class RelationLayout extends SchemaNodeLayout {
    private int                   averageCardinality;
    private final List<ColumnSet> columnSets       = new ArrayList<>();
    private final Layout          layout;
    private double                outlineWidth     = 0;
    private final Relation        r;
    private double                rowHeight        = -1;
    private double                tableColumnWidth = 0;

    public RelationLayout(Layout layout, Relation r) {
        this.layout = layout;
        this.r = r;
    }

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

    @Override
    public double baseOutlineWidth(double width) {
        return width - layout.getNestedInset();
    }

    public double baseRowCellHeight(double extended) {
        return extended - layout.getListCellVerticalInset();
    }

    @Override
    public double baseTableColumnWidth(double width) {
        return width - layout.getNestedInset();
    }

    public JsonControl buildNestedTable(int cardinality) {
        return new NestedTable(cardinality, r, this);
    }

    public JsonControl buildOutline(Function<JsonNode, JsonNode> extractor,
                                    int cardinality) {
        return new Outline(r).build(height, columnSets, extractor, cardinality,
                                    this);
    }

    public double cellHeight(int card, double width) {
        if (r.isFold()) {
            return r.getFold()
                    .cellHeight(averageCardinality * card, width);
        }
        if (height > 0.0) {
            return height;
        }

        int cardinality = r.isSingular() ? 1 : card;
        if (!r.isUseTable()) {
            height = outlineHeight(cardinality);
        } else {
            double elementHeight = elementHeight();
            rowHeight = rowHeight(elementHeight);
            height = tableHeight(cardinality, elementHeight);
        }
        return height;
    }

    @Override
    public void clear() {
        super.clear();
        columnSets.clear();
        rowHeight = -1.0;
    }

    public double compress(double justified) {
        if (r.isUseTable()) {
            return r.justify(baseOutlineWidth(justified));
        }
        justifiedWidth = baseOutlineWidth(justified);
        List<SchemaNode> children = r.getChildren();
        double labelWidth = Layout.snap(children.stream()
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

    public double elementHeight() {
        return r.getChildren()
                .stream()
                .mapToDouble(child -> child.rowHeight(averageCardinality,
                                                      justifiedWidth))
                .max()
                .getAsDouble();
    }

    public double getLayoutWidth() {
        return r.isUseTable() ? tableColumnWidth(tableColumnWidth)
                              : outlineWidth(outlineWidth);
    }

    public double getRowHeight() {
        return rowHeight;
    }

    public double getTableColumnWidth() {
        return tableColumnWidth;
    }

    public double justify(double width) {
        justifiedWidth = baseTableColumnWidth(width);
        double slack = Layout.snap(Math.max(0,
                                            justifiedWidth - tableColumnWidth));
        List<SchemaNode> children = r.getChildren();
        double total = Layout.snap(children.stream()
                                           .map(child -> child.tableColumnWidth())
                                           .reduce((a, b) -> a + b)
                                           .orElse(0.0d));
        children.forEach(child -> {
            double childWidth = child.tableColumnWidth();
            double additional = Layout.snap(slack * (childWidth / total));
            double childJustified = additional + childWidth;
            child.justify(childJustified);
        });
        return justifiedWidth;
    }

    @Override
    public Control label(double labelWidth, String label) {
        return layout.label(labelWidth, label, height);
    }

    @Override
    public double labelWidth(String label) {
        return layout.labelWidth(label);
    }

    public double layout(int cardinality, double width) {
        clear();
        List<SchemaNode> children = r.getChildren();
        double labelWidth = children.stream()
                                    .mapToDouble(child -> child.getLabelWidth())
                                    .max()
                                    .getAsDouble();
        double available = baseOutlineWidth(width - labelWidth);
        outlineWidth = children.stream()
                               .mapToDouble(child -> {
                                   return child.layout(cardinality, available);
                               })
                               .max()
                               .orElse(0d)
                       + labelWidth;
        double extended = outlineWidth(outlineWidth);
        double tableWidth = tableColumnWidth();
        if (tableWidth <= extended) {
            r.nestTable();
            return tableWidth;
        }
        return extended;
    }

    public double measure(Relation parent, JsonNode data, boolean isSingular,
                          Layout layout) {
        double labelWidth = labelWidth(r.getLabel());
        double sum = 0;
        tableColumnWidth = 0;
        int singularChildren = 0;

        for (SchemaNode child : r.getChildren()) {
            ArrayNode aggregate = JsonNodeFactory.instance.arrayNode();
            int cardSum = 0;
            boolean childSingular = false;
            List<JsonNode> datas = data.isArray() ? new ArrayList<>(data.size())
                                                  : Arrays.asList(data);
            if (data.isArray()) {
                data.forEach(n -> datas.add(n));
            }
            for (JsonNode node : datas) {
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
                sum += datas.size() == 0 ? 1
                                         : Math.round(cardSum / datas.size());
            }
            tableColumnWidth += child.measure(null, aggregate, childSingular,
                                              layout);
        }
        int effectiveChildren = r.getChildren()
                                 .size()
                                - singularChildren;
        averageCardinality = Math.max(1,
                                      Math.min(4,
                                               effectiveChildren == 0 ? 1
                                                                      : (int) Math.ceil(sum
                                                                                        / effectiveChildren)));
        tableColumnWidth = Math.max(labelWidth, tableColumnWidth);
        return tableColumnWidth(r.getTableColumnWidth());
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

        JsonControl control = useTable ? r.buildNestedTable(extractor,
                                                            cardinality,
                                                            justified)
                                       : r.buildOutline(extractor, cardinality);

        Control labelControl = label(labelWidth, label);
        control.setPrefWidth(available);
        control.setPrefHeight(height);

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

    public double outlineHeight(int cardinality) {
        return outlineHeight(cardinality, columnSets.stream()
                                                    .mapToDouble(cs -> cs.getCellHeight())
                                                    .sum());
    }

    public double outlineWidth() {
        return outlineWidth(outlineWidth);
    }

    public double outlineWidth(double outlineWidth) {
        return outlineWidth + layout.getNestedInset();
    }

    public double rowHeight(double elementHeight) {
        return elementHeight + layout.getListCellVerticalInset();
    }

    public double rowHeight(int cardinality, double justified) {
        double elementHeight = elementHeight();
        rowHeight = rowHeight(elementHeight);
        height = tableHeight(cardinality, elementHeight);
        return height;
    }

    public double tableColumnWidth() {
        return tableColumnWidth(tableColumnWidth);
    }

    @Override
    public double tableColumnWidth(double width) {
        return width + layout.getNestedInset();
    }

    public double tableHeight(int cardinality, double elementHeight) {
        return (cardinality
                * (elementHeight + layout.getListCellVerticalInset()))
               + layout.getListVerticalInset();
    }

    protected double outlineHeight(int cardinality, double elementHeight) {
        return (cardinality
                * (elementHeight + layout.getListCellVerticalInset()))
               + layout.getListVerticalInset();
    }

    public int getAverageCardinality() {
        return averageCardinality;
    }
}
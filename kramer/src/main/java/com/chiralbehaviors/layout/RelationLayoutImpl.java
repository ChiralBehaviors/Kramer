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

import com.chiralbehaviors.layout.control.JsonControl;
import com.chiralbehaviors.layout.control.NestedTable;
import com.chiralbehaviors.layout.control.Outline;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

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
public class RelationLayoutImpl implements Layout.RelationLayout {
    private final List<ColumnSet> columnSets = new ArrayList<>();
    private final Layout          layout;
    private final Relation        r;

    public RelationLayoutImpl(Layout layout, Relation r) {
        this.layout = layout;
        this.r = r;
    }

    @Override
    public void adjustHeight(double delta) {
        double subDelta = delta / columnSets.size();
        if (subDelta >= 1.0) {
            columnSets.forEach(c -> c.adjustHeight(subDelta));
        }
    }

    @Override
    public void apply(ListCell<JsonNode> cell) {
        layout.getModel()
              .apply(cell, r);
    }

    @Override
    public void apply(ListView<JsonNode> list) {
        layout.getModel()
              .apply(list, r);
    }

    @Override
    public double baseOutlineCellHeight(double cellHeight) {
        return cellHeight - layout.getListCellVerticalInset();
    }

    @Override
    public double baseOutlineWidth(double width) {
        return width - layout.getNestedInset();
    }

    @Override
    public double baseRowCellHeight(double extended) {
        return extended - layout.getListCellVerticalInset();
    }

    @Override
    public double baseTableColumnWidth(double width) {
        return width - layout.getNestedInset();
    }

    @Override
    public JsonControl buildNestedTable(int cardinality) {
        return new NestedTable(cardinality, r, this);
    }

    @Override
    public JsonControl buildOutline(Double height,
                                    Function<JsonNode, JsonNode> extractor,
                                    int cardinality) {
        return new Outline(r).build(height, columnSets, extractor, cardinality,
                                    this);
    }

    @Override
    public double compress(double justified, int averageCardinality) {
        if (r.isUseTable()) {
            return r.justify(baseOutlineWidth(justified));
        }
        double justifiedWidth = baseOutlineWidth(justified);
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

    @Override
    public double justify(double width, double tableColumnWidth) {
        double justifiedWidth = baseTableColumnWidth(width);
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
    public Control label(double labelWidth, String label, double height) {
        return layout.label(labelWidth, label, height);
    }

    @Override
    public double labelWidth(String label) {
        return layout.labelWidth(label);
    }

    @Override
    public double outlineCellHeight(double baseHeight) {
        return baseHeight + layout.getListCellVerticalInset();
    }

    @Override
    public Pair<Consumer<JsonNode>, Parent> outlineElement(String field,
                                                           int cardinality,
                                                           String label,
                                                           double labelWidth,
                                                           Function<JsonNode, JsonNode> extractor,
                                                           double height,
                                                           boolean useTable,
                                                           double justified) {

        double available = justified - labelWidth;

        JsonControl control = useTable ? r.buildNestedTable(extractor,
                                                            cardinality,
                                                            justified)
                                       : r.buildOutline(extractor, cardinality);

        Control labelControl = label(labelWidth, label, available);
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

    @Override
    public double outlineHeight(int cardinality) {
        return outlineHeight(cardinality, columnSets.stream()
                                                    .mapToDouble(cs -> cs.getCellHeight())
                                                    .sum());
    }

    @Override
    public double outlineWidth(double outlineWidth) {
        return outlineWidth + layout.getNestedInset();
    }

    @Override
    public double rowHeight(double elementHeight) {
        return elementHeight + layout.getListCellVerticalInset();
    }

    @Override
    public double tableColumnWidth(double width) {
        return width + layout.getNestedInset();
    }

    @Override
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
}
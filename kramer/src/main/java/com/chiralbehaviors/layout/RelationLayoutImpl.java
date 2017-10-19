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

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.chiralbehaviors.layout.control.JsonControl;
import com.chiralbehaviors.layout.control.NestedTable;
import com.chiralbehaviors.layout.control.Outline;
import com.chiralbehaviors.layout.schema.ColumnSet;
import com.chiralbehaviors.layout.schema.Relation;
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
    private final Layout   layout;
    private final Relation r;

    public RelationLayoutImpl(Layout layout, Relation r) {
        this.layout = layout;
        this.r = r;
    }

    @Override
    public void apply(ListCell<JsonNode> cell) {
        this.layout.getModel()
                   .apply(cell, r);
    }

    @Override
    public void apply(ListView<JsonNode> list) {
        this.layout.getModel()
                   .apply(list, r);
    }

    @Override
    public double baseOutlineCellHeight(double cellHeight) {
        return cellHeight - this.layout.getListCellVerticalInset();
    }

    @Override
    public double baseOutlineWidth(double width) {
        return width - this.layout.getNestedInset();
    }

    @Override
    public double baseRowCellHeight(double extended) {
        return extended - this.layout.getListCellVerticalInset();
    }

    @Override
    public double baseTableColumnWidth(double width) {
        return width - this.layout.getNestedInset();
    }

    @Override
    public JsonControl buildNestedTable(int cardinality) {
        return new NestedTable(cardinality, r);
    }

    @Override
    public JsonControl buildOutline(Double height, List<ColumnSet> columnSets,
                                    Function<JsonNode, JsonNode> extractor,
                                    int cardinality) {
        return new Outline(r).build(height, columnSets, extractor, cardinality);
    }

    @Override
    public Control label(double labelWidth, String label, double height) {
        return this.layout.label(labelWidth, label, height);
    }

    @Override
    public double labelWidth(String label) {
        return this.layout.totalTextWidth(this.layout.textWidth(label));
    }

    @Override
    public double outlineCellHeight(double baseHeight) {
        return baseHeight + this.layout.getListCellVerticalInset();
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
    public double outlineHeight(int cardinality, double elementHeight) {
        return (cardinality
                * (elementHeight + this.layout.getListCellVerticalInset()))
               + this.layout.getListVerticalInset();
    }

    @Override
    public double outlineWidth(double outlineWidth) {
        return outlineWidth + this.layout.getNestedInset();
    }

    @Override
    public double rowHeight(double elementHeight) {
        return elementHeight + this.layout.getListCellVerticalInset();
    }

    @Override
    public double tableColumnWidth(double width) {
        return width + this.layout.getNestedInset();
    }

    @Override
    public double tableHeight(int cardinality, double elementHeight) {
        return (cardinality
                * (elementHeight + this.layout.getListCellVerticalInset()))
               + this.layout.getListVerticalInset();
    }
}
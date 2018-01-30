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

package com.chiralbehaviors.layout.table;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.cell.Hit;
import com.chiralbehaviors.layout.cell.HorizontalCell;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.LayoutContainer;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.cell.control.FocusTraversalNode;
import com.chiralbehaviors.layout.cell.control.FocusTraversalNode.Bias;
import com.chiralbehaviors.layout.cell.control.MouseHandler;
import com.chiralbehaviors.layout.cell.control.MultipleCellSelection;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.layout.Region;

/**
 * @author halhildebrand
 *
 */
public class NestedCell extends HorizontalCell<NestedCell> implements
        LayoutContainer<JsonNode, NestedCell, LayoutCell<? extends Region>> {
    private static final String                                                 NESTED_CELL_CLASS     = "nested-cell";
    private static final String                                                 SCHEMA_CLASS_TEMPLATE = "%s-nested-cell";
    private static final String                                                 STYLE_SHEET           = "nested-cell.css";
    private List<LayoutCell<? extends Region>>                                  cells                 = new ArrayList<>();
    private final List<Consumer<JsonNode>>                                      consumers             = new ArrayList<>();
    private final FocusTraversal<?>                                             focus;
    private int                                                                 index;
    private final MouseHandler                                                  mouseModel;
    private final MultipleCellSelection<JsonNode, LayoutCell<? extends Region>> selectionModel;

    public NestedCell(RelationLayout layout,
                      FocusTraversal<NestedCell> parentTraversal, Style model) {
        this(layout.getField(), parentTraversal);
        layout.forEach(child -> {
            LayoutCell<? extends Region> cell = child.buildColumn(layout.baseRowCellHeight(layout.getCellHeight()),
                                                                  focus, model);
            cells.add(cell);
            consumers.add(item -> cell.updateItem(child.extractFrom(item)));
            getChildren().add(cell.getNode());
        });
    }

    public NestedCell(String field) {
        this(field, null);
    }

    public NestedCell(String field,
                      FocusTraversal<NestedCell> parentTraversal) {
        super(STYLE_SHEET);
        this.initialize(NESTED_CELL_CLASS);
        getStyleClass().addAll(String.format(SCHEMA_CLASS_TEMPLATE, field));
        selectionModel = buildSelectionModel(i -> null, () -> cells.size(),
                                             i -> cells.get(i));
        focus = new FocusTraversalNode<LayoutCell<? extends Region>>(parentTraversal,
                                                                     selectionModel,
                                                                     Bias.HORIZONTAL) {

            @Override
            protected NestedCell getContainer() {
                return NestedCell.this;
            }
        };
        mouseModel = bind(selectionModel);
    }

    @Override
    public void activate() {
        focus.setCurrent();
    }

    @Override
    public void dispose() {
        super.dispose();
        mouseModel.unbind();
        focus.unbind();
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public Hit<LayoutCell<? extends Region>> hit(double x, double y) {
        return hit(x, y, cells);
    }

    @Override
    public void updateIndex(int index) {
        boolean active = ((index % 2) == 0);
        pseudoClassStateChanged(PSEUDO_CLASS_EVEN, active);
        pseudoClassStateChanged(PSEUDO_CLASS_ODD, !active);
        this.index = index;
    }

    @Override
    public void updateItem(JsonNode item) {
        consumers.forEach(c -> c.accept(item));
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_EMPTY, item == null);
    }
}

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

package com.chiralbehaviors.layout.outline;

import java.util.Collection;
import java.util.Collections;

import com.chiralbehaviors.layout.SchemaNodeLayout;
import com.chiralbehaviors.layout.cell.Hit;
import com.chiralbehaviors.layout.cell.HorizontalCell;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.LayoutContainer;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.style.RelationStyle;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.beans.InvalidationListener;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;

/**
 * @author halhildebrand
 *
 */
public class OutlineElement extends HorizontalCell<OutlineElement>
        implements LayoutContainer<JsonNode, OutlineElement, LayoutCell<?>> {

    private static final String                  DEFAULT_STYLE         = "outline-element";
    private static final String                  SCHEMA_CLASS_TEMPLATE = "%s-outline-element";
    private static final String                  STYLE_SHEET           = "outline-element.css";

    private final LayoutCell<?>                  cell;
    private int                                  index;
    private final FocusTraversal<OutlineElement> parentTraversal;

    public OutlineElement(SchemaNodeLayout layout, String field,
                          int cardinality, double labelWidth,
                          double elementHeight,
                          FocusTraversal<OutlineElement> parentTraversal,
                          Style model, RelationStyle style) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
        this.cell = layout.buildControl(parentTraversal, model);
        this.parentTraversal = parentTraversal;
        OutlineElement node = getNode();
        node.focusedProperty()
            .addListener((InvalidationListener) property -> {
                if (node.isFocused()) {
                    parentTraversal.setCurrent();
                }
            });

        cell.getNode()
            .setMinHeight(elementHeight);
        cell.getNode()
            .setPrefHeight(elementHeight);
        cell.getNode()
            .setMaxHeight(elementHeight);
        Label label = layout.label(labelWidth, elementHeight);
        getChildren().addAll(label, cell.getNode());

    }

    public OutlineElement(String field) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
        this.cell = null;
        this.parentTraversal = null;
    }

    public OutlineElement(String field, SchemaNodeLayout layout,
                          int cardinality, double labelWidth,
                          FocusTraversal<OutlineElement> parentTraversal,
                          Style model, RelationStyle style) {
        this(layout, field, cardinality, labelWidth, layout.getHeight(),
             parentTraversal, model, style);
    }

    @Override
    public void activate() {
        parentTraversal.setCurrent();
    }

    @Override
    public Collection<LayoutCell<?>> getContained() {
        return Collections.singletonList(cell);
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public Hit<LayoutCell<?>> hit(double x, double y) {
        Hit<LayoutCell<?>> hit = new Hit<LayoutCell<?>>() {
            @Override
            public LayoutCell<?> getCell() {
                return cell;
            }

            @Override
            public int getCellIndex() {
                return 0;
            }

            @Override
            public Point2D getCellOffset() {
                return new Point2D(0, 0);
            }

            @Override
            public Point2D getOffsetAfterCells() {
                return new Point2D(0, 0);
            }

            @Override
            public Point2D getOffsetBeforeCells() {
                return new Point2D(0, 0);
            }

            @Override
            public boolean isAfterCells() {
                return false;
            }

            @Override
            public boolean isBeforeCells() {
                return false;
            }

            @Override
            public boolean isCellHit() {
                return true;
            }
        };
        return cell.getNode()
                   .contains(new Point2D(x, y)) ? hit : null;
    }

    @Override
    public void updateIndex(int index) {
        this.index = index;
    }

    @Override
    public void updateItem(JsonNode item) {
        cell.updateItem(item);
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED, item != null);
        getNode().pseudoClassStateChanged(PSEUDO_CLASS_EMPTY, item == null);
    }
}

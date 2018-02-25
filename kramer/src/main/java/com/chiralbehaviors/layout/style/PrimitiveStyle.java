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

package com.chiralbehaviors.layout.style;

import static com.chiralbehaviors.layout.cell.control.SelectionEvent.DOUBLE_SELECT;
import static com.chiralbehaviors.layout.cell.control.SelectionEvent.SINGLE_SELECT;
import static com.chiralbehaviors.layout.cell.control.SelectionEvent.TRIPLE_SELECT;

import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusTraversal;
import com.chiralbehaviors.layout.cell.control.MouseHandler;
import com.chiralbehaviors.layout.cell.control.SelectionEvent;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * @author halhildebrand
 *
 */
abstract public class PrimitiveStyle extends NodeStyle {

    abstract public class PrimitiveLayoutCell<C extends Region>
            implements LayoutCell<C> {
        public static final String DEFAULT_STYLE = "primitive";
        private int                index;
        private final MouseHandler mouseHandler;

        public PrimitiveLayoutCell(PrimitiveLayout p, String style,
                                   FocusTraversal<?> parentTraversal) {
            initialize(p.getField());
            initialize(DEFAULT_STYLE);
            getNode().getStyleClass()
                     .addAll(style, p.getField());
            mouseHandler = new MouseHandler(new Duration(300)) {

                @Override
                public void doubleClick(MouseEvent mouseEvent) {
                    if (getNode().contains(new Point2D(mouseEvent.getX(),
                                                       mouseEvent.getY()))) {
                        SelectionEvent event = new SelectionEvent(PrimitiveLayoutCell.this,
                                                                  DOUBLE_SELECT);
                        if (!parentTraversal.propagate(event)) {
                            getNode().fireEvent(event);
                        }
                    }
                }

                @Override
                public Node getNode() {
                    return PrimitiveLayoutCell.this.getNode();
                }

                @Override
                public void singleClick(MouseEvent mouseEvent) {
                    if (getNode().contains(new Point2D(mouseEvent.getX(),
                                                       mouseEvent.getY()))) {
                        SelectionEvent event = new SelectionEvent(PrimitiveLayoutCell.this,
                                                                  SINGLE_SELECT);
                        if (!parentTraversal.propagate(event)) {
                            getNode().fireEvent(event);
                        }
                    }
                }

                @Override
                public void tripleClick(MouseEvent mouseEvent) {
                    if (getNode().contains(new Point2D(mouseEvent.getX(),
                                                       mouseEvent.getY()))) {
                        SelectionEvent event = new SelectionEvent(PrimitiveLayoutCell.this,
                                                                  TRIPLE_SELECT);
                        if (!parentTraversal.propagate(event)) {
                            getNode().fireEvent(event);
                        }
                    }
                }
            };
        }

        public void dispose() {
            mouseHandler.unbind();
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public void updateIndex(int index) {
            this.index = index;
        }

        @Override
        public void updateItem(JsonNode item) {
            getNode().pseudoClassStateChanged(PSEUDO_CLASS_FILLED,
                                              item != null);
            getNode().pseudoClassStateChanged(PSEUDO_CLASS_EMPTY, item == null);
        }
    }

    public static class PrimitiveTextStyle extends PrimitiveStyle {

        public static String     PRIMITIVE_TEXT_CLASS = "primitive-text";

        private final LabelStyle primitiveStyle;

        public PrimitiveTextStyle(LabelStyle labelStyle, Insets listInsets,
                                  LabelStyle primitiveStyle) {
            super(labelStyle, listInsets);
            this.primitiveStyle = primitiveStyle;
        }

        public LayoutCell<?> build(FocusTraversal<?> pt, PrimitiveLayout p) {
            Label label = new Label();
            label.getStyleClass()
                 .clear();
            label.setMinSize(p.getJustifiedWidth(), p.getCellHeight());
            label.setPrefSize(p.getJustifiedWidth(), p.getCellHeight());
            label.setMaxSize(p.getJustifiedWidth(), p.getCellHeight());
            label.focusedProperty()
                 .addListener((InvalidationListener) property -> {
                     if (label.isFocused()) {
                         pt.setCurrent();
                     }
                 });

            return new PrimitiveLayoutCell<Region>(p, PRIMITIVE_TEXT_CLASS,
                                                   pt) {
                @Override
                public Label getNode() {
                    return label;
                }

                @Override
                public boolean isReusable() {
                    return true;
                }

                @Override
                public void updateItem(JsonNode item) {
                    super.updateItem(item);
                    label.setText(SchemaNode.asText(item));
                }
            };

        }

        public double getHeight(double maxWidth, double justified) {
            return primitiveStyle.getHeight(Math.ceil((maxWidth / justified)));
        }

        public double width(JsonNode row) {
            return primitiveStyle.width(Style.toString(row))
                   + primitiveStyle.getHorizontalInset();
        }

    }

    private final Insets listInsets;

    public PrimitiveStyle(LabelStyle labelStyle, Insets listInsets) {
        super(labelStyle);
        this.listInsets = listInsets;
    }

    abstract public LayoutCell<?> build(FocusTraversal<?> pt,
                                        PrimitiveLayout p);

    abstract public double getHeight(double maxWidth, double justified);

    public double getListVerticalInset() {
        return listInsets.getTop() + listInsets.getBottom();
    }

    abstract public double width(JsonNode row);
}

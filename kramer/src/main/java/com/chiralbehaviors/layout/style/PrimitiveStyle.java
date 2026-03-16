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

import com.chiralbehaviors.layout.MeasureResult;
import com.chiralbehaviors.layout.NumericStats;
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
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
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

        public static final String PRIMITIVE_TEXT_CLASS = "primitive-text";

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

    public static class PrimitiveBarStyle extends PrimitiveStyle {

        public static final String PRIMITIVE_BAR_CLASS = "primitive-bar";

        private final LabelStyle primitiveStyle;

        public PrimitiveBarStyle(LabelStyle labelStyle, Insets listInsets,
                                 LabelStyle primitiveStyle) {
            super(labelStyle, listInsets);
            this.primitiveStyle = primitiveStyle;
        }

        @Override
        public LayoutCell<?> build(FocusTraversal<?> pt, PrimitiveLayout p) {
            StackPane pane = new StackPane();
            pane.setMinSize(p.getJustifiedWidth(), p.getCellHeight());
            pane.setPrefSize(p.getJustifiedWidth(), p.getCellHeight());
            pane.setMaxSize(p.getJustifiedWidth(), p.getCellHeight());

            Rectangle bar = new Rectangle(0, p.getCellHeight());
            bar.getStyleClass().add(PRIMITIVE_BAR_CLASS);
            pane.getChildren().add(bar);
            // Align the bar to the left inside the StackPane
            javafx.scene.layout.StackPane.setAlignment(bar, javafx.geometry.Pos.CENTER_LEFT);

            return new PrimitiveLayoutCell<Region>(p, PRIMITIVE_BAR_CLASS, pt) {
                @Override
                public StackPane getNode() {
                    return pane;
                }

                @Override
                public boolean isReusable() {
                    return true;
                }

                @Override
                public void updateItem(com.fasterxml.jackson.databind.JsonNode item) {
                    super.updateItem(item);
                    if (item == null || item.isNull()) {
                        bar.setWidth(0);
                        return;
                    }
                    double value = item.asDouble();
                    double justifiedWidth = p.getJustifiedWidth();
                    MeasureResult mr = p.getMeasureResult();
                    NumericStats ns = (mr != null) ? mr.numericStats() : null;
                    double min = (ns != null) ? ns.numericMin() : 0.0;
                    double max = (ns != null) ? ns.numericMax() : 0.0;
                    double range = max - min;
                    double fraction = (range == 0.0) ? 1.0 : (value - min) / range;
                    bar.setWidth(fraction * justifiedWidth);
                }
            };
        }

        @Override
        public double getHeight(double maxWidth, double justified) {
            return primitiveStyle.getHeight(1);
        }

        @Override
        public double width(com.fasterxml.jackson.databind.JsonNode row) {
            return 0.0;
        }

    }

    private final Insets listInsets;
    private final double minValueWidth            = 30;
    private final double maxTablePrimitiveWidth   = 350.0;
    private final double verticalHeaderThreshold  = 1.5;
    private final double variableLengthThreshold  = 2.0;
    private final double outlineSnapValueWidth    = 0.0;

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

    public double getMinValueWidth() {
        return minValueWidth;
    }

    public double getMaxTablePrimitiveWidth() {
        return maxTablePrimitiveWidth;
    }

    public double getVerticalHeaderThreshold() {
        return verticalHeaderThreshold;
    }

    public double getVariableLengthThreshold() {
        return variableLengthThreshold;
    }

    public double getOutlineSnapValueWidth() {
        return outlineSnapValueWidth;
    }

    abstract public double width(JsonNode row);
}

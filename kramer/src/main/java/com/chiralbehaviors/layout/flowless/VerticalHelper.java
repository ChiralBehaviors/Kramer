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

package com.chiralbehaviors.layout.flowless;

import org.reactfx.value.Val;
import org.reactfx.value.Var;

import javafx.beans.property.DoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;

/**
 * Implementation of {@link OrientationHelper} where {@code breadth} represents
 * width of the node/viewport and {@code length} represents the height of the
 * node/viewport. "layoutX" is {@link javafx.scene.Node#layoutX} and "layoutY"
 * is {@link javafx.scene.Node#layoutY}. "viewport offset" values are based on
 * height. The viewport's "top" and "bottom" edges are either it's top/bottom
 * edges (See {@link com.chiralbehaviors.layout.flowless.VirtualFlow.Gravity}).
 */
public final class VerticalHelper implements OrientationHelper {

    @Override
    public double breadth(Bounds bounds) {
        return bounds.getWidth();
    }

    @Override
    public <C extends Cell<?, ?>> VirtualFlowHit<C> cellHit(int itemIndex,
                                                            C cell, double bOff,
                                                            double lOff) {
        return VirtualFlowHit.cellHit(itemIndex, cell, bOff, lOff);
    }

    @Override
    public Var<Double> estimatedScrollXProperty(VirtualFlow<?, ?> content) {
        return content.breadthOffsetProperty();
    }

    @Override
    public Var<Double> estimatedScrollYProperty(VirtualFlow<?, ?> content) {
        return content.lengthOffsetEstimateProperty();
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    public double getX(double x, double y) {
        return x;
    }

    @Override
    public double getY(double x, double y) {
        return y;
    }

    @Override
    public Val<Double> heightEstimateProperty(VirtualFlow<?, ?> content) {
        return content.totalLengthEstimateProperty();
    }

    @Override
    public <C extends Cell<?, ?>> VirtualFlowHit<C> hitAfterCells(double bOff,
                                                                  double lOff) {
        return VirtualFlowHit.hitAfterCells(bOff, lOff);
    }

    @Override
    public <C extends Cell<?, ?>> VirtualFlowHit<C> hitBeforeCells(double bOff,
                                                                   double lOff) {
        return VirtualFlowHit.hitBeforeCells(bOff, lOff);
    }

    @Override
    public double layoutX(Node node) {
        return node.getLayoutX();
    }

    @Override
    public double layoutY(Node node) {
        return node.getLayoutY();
    }

    @Override
    public DoubleProperty layoutYProperty(Node node) {
        return node.layoutYProperty();
    }

    @Override
    public double length(Bounds bounds) {
        return bounds.getHeight();
    }

    @Override
    public double minBreadth(Node node) {
        return node.minWidth(-1);
    }

    @Override
    public double minX(Bounds bounds) {
        return bounds.getMinX();
    }

    @Override
    public double minY(Bounds bounds) {
        return bounds.getMinY();
    }

    @Override
    public double prefBreadth(Node node) {
        return node.prefWidth(-1);
    }

    @Override
    public double prefLength(Node node, double breadth) {
        return node.prefHeight(breadth);
    }

    @Override
    public void relocate(Node node, double b0, double l0) {
        node.relocate(b0, l0);
    }

    @Override
    public void resize(Node node, double breadth, double length) {
        node.resize(breadth, length);
    }

    @Override
    public void resizeRelocate(Node node, double b0, double l0, double breadth,
                               double length) {
        node.resizeRelocate(b0, l0, breadth, length);
    }

    @Override
    public void scrollHorizontallyBy(VirtualFlow<?, ?> content, double dx) {
        content.scrollBreadth(dx);
    }

    @Override
    public void scrollHorizontallyToPixel(VirtualFlow<?, ?> content,
                                          double pixel) {
        content.setBreadthOffset(pixel);
    }

    @Override
    public void scrollVerticallyBy(VirtualFlow<?, ?> content, double dy) {
        content.scrollLength(dy);
    }

    @Override
    public void scrollVerticallyToPixel(VirtualFlow<?, ?> content,
                                        double pixel) { // length
        content.setLengthOffset(pixel);
    }

    @Override
    public Val<Double> widthEstimateProperty(VirtualFlow<?, ?> content) {
        return content.totalBreadthEstimateProperty();
    }
}
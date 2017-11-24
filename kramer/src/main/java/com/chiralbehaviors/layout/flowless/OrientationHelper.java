package com.chiralbehaviors.layout.flowless;

import org.reactfx.value.Val;
import org.reactfx.value.Var;

import javafx.beans.property.DoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;

/**
 * Implementation of {@link OrientationHelper} where {@code length} represents
 * width of the node/viewport and {@code breadth} represents the height of the
 * node/viewport. "layoutY" is {@link javafx.scene.Node#layoutX} and "layoutX"
 * is {@link javafx.scene.Node#layoutY}. "viewport offset" values are based on
 * width. The viewport's "top" and "bottom" edges are either it's left/right
 * edges (See {@link com.chiralbehaviors.layout.flowless.VirtualFlow.Gravity}).
 */
final class HorizontalHelper implements OrientationHelper {

    @Override
    public double breadth(Bounds bounds) {
        return bounds.getHeight();
    }

    @Override
    public <C extends Cell<?, ?>> VirtualFlowHit<C> cellHit(int itemIndex,
                                                            C cell, double bOff,
                                                            double lOff) {
        return VirtualFlowHit.cellHit(itemIndex, cell, lOff, bOff);
    }

    @Override
    public Var<Double> estimatedScrollXProperty(VirtualFlow<?, ?> content) {
        return content.lengthOffsetEstimateProperty();
    }

    @Override
    public Var<Double> estimatedScrollYProperty(VirtualFlow<?, ?> content) {
        return content.breadthOffsetProperty();
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.VERTICAL;
    }

    @Override
    public double getX(double x, double y) {
        return y;
    }

    @Override
    public double getY(double x, double y) {
        return x;
    }

    @Override
    public Val<Double> heightEstimateProperty(VirtualFlow<?, ?> content) {
        return content.totalBreadthEstimateProperty();
    }

    @Override
    public <C extends Cell<?, ?>> VirtualFlowHit<C> hitAfterCells(double bOff,
                                                                  double lOff) {
        return VirtualFlowHit.hitAfterCells(lOff, bOff);
    }

    @Override
    public <C extends Cell<?, ?>> VirtualFlowHit<C> hitBeforeCells(double bOff,
                                                                   double lOff) {
        return VirtualFlowHit.hitBeforeCells(lOff, bOff);
    }

    @Override
    public double layoutX(Node node) {
        return node.getLayoutY();
    }

    @Override
    public double layoutY(Node node) {
        return node.getLayoutX();
    }

    @Override
    public DoubleProperty layoutYProperty(Node node) {
        return node.layoutXProperty();
    }

    @Override
    public double length(Bounds bounds) {
        return bounds.getWidth();
    }

    @Override
    public double minBreadth(Node node) {
        return node.minHeight(-1);
    }

    @Override
    public double minX(Bounds bounds) {
        return bounds.getMinY();
    }

    @Override
    public double minY(Bounds bounds) {
        return bounds.getMinX();
    }

    @Override
    public double prefBreadth(Node node) {
        return node.prefHeight(-1);
    }

    @Override
    public double prefLength(Node node, double breadth) {
        return node.prefWidth(breadth);
    }

    @Override
    public void relocate(Node node, double b0, double l0) {
        node.relocate(l0, b0);
    }

    @Override
    public void resize(Node node, double breadth, double length) {
        node.resize(length, breadth);
    }

    @Override
    public void resizeRelocate(Node node, double b0, double l0, double breadth,
                               double length) {
        node.resizeRelocate(l0, b0, length, breadth);
    }

    @Override
    public void scrollHorizontallyBy(VirtualFlow<?, ?> content, double dx) {
        content.scrollLength(dx);
    }

    @Override
    public void scrollHorizontallyToPixel(VirtualFlow<?, ?> content,
                                          double pixel) {
        content.setLengthOffset(pixel);
    }

    @Override
    public void scrollVerticallyBy(VirtualFlow<?, ?> content, double dy) {
        content.scrollBreadth(dy);
    }

    @Override
    public void scrollVerticallyToPixel(VirtualFlow<?, ?> content,
                                        double pixel) {
        content.setBreadthOffset(pixel);
    }

    @Override
    public Val<Double> widthEstimateProperty(VirtualFlow<?, ?> content) {
        return content.totalLengthEstimateProperty();
    }
}

/**
 * Helper class for returning the correct value (should the {@code width} or
 * {@code height} be returned?) or calling the correct method (should
 * {@code setWidth(args)} or {@code setHeight(args)}, so that one one class can
 * be used instead of a generic with two implementations. See its
 * implementations for more details ({@link VerticalHelper} and
 * {@link HorizontalHelper}) on what "layoutX", "layoutY", and "viewport offset"
 * values represent.
 */
interface OrientationHelper {
    double breadth(Bounds bounds);

    default double breadth(Cell<?, ?> cell) {
        return breadth(cell.getNode());
    }

    default double breadth(Node node) {
        return breadth(node.getLayoutBounds());
    }

    <C extends Cell<?, ?>> VirtualFlowHit<C> cellHit(int itemIndex, C cell,
                                                     double bOff, double lOff);

    Var<Double> estimatedScrollXProperty(VirtualFlow<?, ?> content);

    Var<Double> estimatedScrollYProperty(VirtualFlow<?, ?> content);

    Orientation getContentBias();

    double getX(double x, double y);

    double getY(double x, double y);

    Val<Double> heightEstimateProperty(VirtualFlow<?, ?> content);

    <C extends Cell<?, ?>> VirtualFlowHit<C> hitAfterCells(double bOff,
                                                           double lOff);

    <C extends Cell<?, ?>> VirtualFlowHit<C> hitBeforeCells(double bOff,
                                                            double lOff);

    double layoutX(Node node);

    double layoutY(Node node);

    DoubleProperty layoutYProperty(Node node);

    double length(Bounds bounds);

    default double length(Cell<?, ?> cell) {
        return length(cell.getNode());
    }

    default double length(Node node) {
        return length(node.getLayoutBounds());
    }

    default double maxX(Bounds bounds) {
        return minX(bounds) + breadth(bounds);
    }

    default double maxX(Cell<?, ?> cell) {
        return maxX(cell.getNode());
    }

    default double maxX(Node node) {
        return minX(node) + breadth(node);
    }

    default double maxY(Bounds bounds) {
        return minY(bounds) + length(bounds);
    }

    default double maxY(Cell<?, ?> cell) {
        return maxY(cell.getNode());
    }

    default double maxY(Node node) {
        return minY(node) + length(node);
    }

    default double minBreadth(Cell<?, ?> cell) {
        return minBreadth(cell.getNode());
    }

    double minBreadth(Node node);

    double minX(Bounds bounds);

    default double minX(Cell<?, ?> cell) {
        return minX(cell.getNode());
    }

    default double minX(Node node) {
        return layoutX(node) + minX(node.getLayoutBounds());
    }

    double minY(Bounds bounds);

    default double minY(Cell<?, ?> cell) {
        return minY(cell.getNode());
    }

    default double minY(Node node) {
        return layoutY(node) + minY(node.getLayoutBounds());
    }

    default Val<Double> minYProperty(Cell<?, ?> cell) {
        return minYProperty(cell.getNode());
    }

    default Val<Double> minYProperty(Node node) {
        return Val.combine(layoutYProperty(node), node.layoutBoundsProperty(),
                           (layoutY, layoutBounds) -> layoutY.doubleValue()
                                                      + minY(layoutBounds));
    }

    double prefBreadth(Node node);

    default double prefLength(Cell<?, ?> cell, double breadth) {
        return prefLength(cell.getNode(), breadth);
    }

    double prefLength(Node node, double breadth);

    default void relocate(Cell<?, ?> cell, double b0, double l0) {
        relocate(cell.getNode(), b0, l0);
    }

    void relocate(Node node, double b0, double l0);

    default void resize(Cell<?, ?> cell, double breadth, double length) {
        resize(cell.getNode(), breadth, length);
    }

    void resize(Node node, double breadth, double length);

    void resizeRelocate(Node node, double b0, double l0, double breadth,
                        double length);

    void scrollHorizontallyBy(VirtualFlow<?, ?> content, double dx);

    void scrollHorizontallyToPixel(VirtualFlow<?, ?> content, double pixel);

    void scrollVerticallyBy(VirtualFlow<?, ?> content, double dy);

    void scrollVerticallyToPixel(VirtualFlow<?, ?> content, double pixel);

    Val<Double> widthEstimateProperty(VirtualFlow<?, ?> content);
}
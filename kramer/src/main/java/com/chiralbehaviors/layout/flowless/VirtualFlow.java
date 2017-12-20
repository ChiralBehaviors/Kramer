package com.chiralbehaviors.layout.flowless;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import org.reactfx.collection.MemoizationList;
import org.reactfx.util.Lists;
import org.reactfx.value.Val;
import org.reactfx.value.Var;

import com.chiralbehaviors.layout.cell.FocusTraversal;
import com.chiralbehaviors.layout.cell.FocusTraversal.Bias;
import com.chiralbehaviors.layout.cell.Hit;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.MouseHandler;
import com.chiralbehaviors.layout.cell.MultipleCellSelection;
import com.chiralbehaviors.layout.cell.RegionCell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/**
 * A VirtualFlow is a memory-efficient viewport that only renders enough of its
 * content to completely fill up the viewport through its {@link Navigator}. The
 * flow sequentially lays out the {@link javafx.scene.Node}s of the
 * {@link Cell}s until the viewport is completely filled up or it has no
 * additional cell's nodes to render.
 *
 * <p>
 * Since this viewport does not fully render all of its content, the scroll
 * values are estimates based on the nodes that are currently displayed in the
 * viewport. If every node that could be rendered is the same width or same
 * height, then the corresponding scroll values (e.g., scrollX or totalX) are
 * accurate.
 * </p>
 *
 * @param <JsonNode>
 *            the model content that the {@link Cell#getNode() cell's node}
 *            renders
 * @param <C>
 *            the {@link Cell} that can render the model with a
 *            {@link javafx.scene.Node}.
 */
public class VirtualFlow<C extends LayoutCell<?>>
        extends RegionCell<VirtualFlow<LayoutCell<?>>, C> {
    private static class CellHit<C extends Cell<?, ?>> extends Hit<C> {
        private final C       cell;
        private final int     cellIdx;
        private final Point2D cellOffset;

        CellHit(int cellIdx, C cell, Point2D cellOffset) {
            super();
            this.cellIdx = cellIdx;
            this.cell = cell;
            this.cellOffset = cellOffset;
        }

        @Override
        public C getCell() {
            return cell;
        }

        @Override
        public int getCellIndex() {
            return cellIdx;
        }

        @Override
        public Point2D getCellOffset() {
            return cellOffset;
        }

        @Override
        public Point2D getOffsetAfterCells() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Point2D getOffsetBeforeCells() {
            throw new UnsupportedOperationException();
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
    }

    private static class HitAfterCells<C extends Cell<?, ?>> extends Hit<C> {
        private final Point2D offset;

        HitAfterCells(Point2D offset) {
            this.offset = offset;
        }

        @Override
        public C getCell() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getCellIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Point2D getCellOffset() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Point2D getOffsetAfterCells() {
            return offset;
        }

        @Override
        public Point2D getOffsetBeforeCells() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAfterCells() {
            return true;
        }

        @Override
        public boolean isBeforeCells() {
            return false;
        }

        @Override
        public boolean isCellHit() {
            return false;
        }
    }

    private static class HitBeforeCells<C extends Cell<?, ?>> extends Hit<C> {
        private final Point2D offset;

        HitBeforeCells(Point2D offset) {
            this.offset = offset;
        }

        @Override
        public C getCell() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getCellIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Point2D getCellOffset() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Point2D getOffsetAfterCells() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Point2D getOffsetBeforeCells() {
            return offset;
        }

        @Override
        public boolean isAfterCells() {
            return false;
        }

        @Override
        public boolean isBeforeCells() {
            return true;
        }

        @Override
        public boolean isCellHit() {
            return false;
        }
    }

    public Hit<C> cellHit(int cellIndex, C cell, double x, double y) {
        return new CellHit<>(cellIndex, cell, new Point2D(x, y));
    }

    public Hit<C> hitAfterCells(double x, double y) {
        return new HitAfterCells<>(new Point2D(x, y));
    }

    public Hit<C> hitBeforeCells(double x, double y) {
        return new HitBeforeCells<>(new Point2D(x, y));
    }

    protected final FocusTraversal<C>                focus;
    protected final ObservableList<JsonNode>         items;
    protected final MouseHandler                     mouseHandler;
    protected final ScrollHandler                    scrollHandler = new ScrollHandler(this);
    private final CellListManager<C>                 cellListManager;
    private final CellPositioner<C>                  cellPositioner;
    private final Var<Double>                        lengthOffsetEstimate;
    private final Navigator<C>                       navigator;
    private final MultipleCellSelection<JsonNode, C> selectionModel;
    private final SizeTracker                        sizeTracker;

    {

    }

    public VirtualFlow(String styleSheet) {
        this(styleSheet, 0, 0, FXCollections.observableArrayList(),
             (n, f) -> null, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public VirtualFlow(String styleSheet, double cellBreadth, double cellLength,
                       ObservableList<JsonNode> observableList,
                       BiFunction<JsonNode, FocusTraversal<C>, C> factory,
                       FocusTraversal<?> parentTraversal) {
        super(styleSheet);
        this.getStyleClass()
            .add("virtual-flow");
        this.items = observableList;
        selectionModel = buildSelectionModel(i -> observableList.get(i),
                                             () -> observableList.size(),
                                             i -> getCell(i));
        focus = new FocusTraversal((FocusTraversal<?>) parentTraversal,
                                   selectionModel, Bias.VERTICAL) {

            @Override
            protected Node getNode() {
                return VirtualFlow.this;
            }

        };
        this.cellListManager = new CellListManager<C>(observableList,
                                                      item -> factory.apply(item,
                                                                            focus));
        MemoizationList<C> cells = cellListManager.getLazyCellList();
        this.sizeTracker = new SizeTracker(cellBreadth, cellLength,
                                           layoutBoundsProperty(), cells);
        this.cellPositioner = new CellPositioner<>(cellListManager,
                                                   sizeTracker);
        this.navigator = new Navigator<>(cellListManager, cellPositioner,
                                         sizeTracker);

        getChildren().add(navigator);
        clipProperty().bind(Val.map(layoutBoundsProperty(),
                                    b -> new Rectangle(b.getWidth(),
                                                       b.getHeight())));

        lengthOffsetEstimate = sizeTracker.lengthOffsetEstimateProperty()
                                          .asVar(this::setLengthOffset);
        mouseHandler = bind(selectionModel);
    }

    public Bounds cellToViewport(C cell, Bounds bounds) {
        return cell.getNode()
                   .localToParent(bounds);
    }

    public Point2D cellToViewport(C cell, double x, double y) {
        return cell.getNode()
                   .localToParent(x, y);
    }

    public Point2D cellToViewport(C cell, Point2D point) {
        return cell.getNode()
                   .localToParent(point);
    }

    public void dispose() {
        focus.unbind();
        navigator.dispose();
        sizeTracker.dispose();
        cellListManager.dispose();
        scrollHandler.unbind();
    }

    /**
     * If the item is out of view, instantiates a new cell for the item. The
     * returned cell will be properly sized, but not properly positioned
     * relative to the cells in the viewport, unless it is itself in the
     * viewport.
     *
     * @return Cell for the given item. The cell will be valid only until the
     *         next layout pass. It should therefore not be stored. It is
     *         intended to be used for measurement purposes only.
     */
    public C getCell(int itemIndex) {
        Lists.checkIndex(itemIndex, items.size());
        return cellPositioner.getSizedCell(itemIndex);
    }

    /**
     * This method calls {@link #layout()} as a side-effect to insure that the
     * VirtualFlow is up-to-date in light of any changes
     */
    public Optional<C> getCellIfVisible(int itemIndex) {
        // insure cells are up-to-date in light of any changes
        layout();
        return cellPositioner.getCellIfVisible(itemIndex);
    }

    @Override
    public final Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    public FocusTraversal<?> getFocusTraversal() {
        return focus;
    }

    public ObservableList<JsonNode> getItems() {
        return items;
    }

    public MultipleCellSelection<JsonNode, C> getSelectionModel() {
        return selectionModel;
    }

    /**
     * Hits this virtual flow at the given coordinates.
     *
     * @param x
     *            x offset from the left edge of the viewport
     * @param y
     *            y offset from the top edge of the viewport
     * @return hit info containing the cell that was hit and coordinates
     *         relative to the cell. If the hit was before the cells (i.e. above
     *         a vertical flow content or left of a horizontal flow content),
     *         returns a <em>hit before cells</em> containing offset from the
     *         top left corner of the content. If the hit was after the cells
     *         (i.e. below a vertical flow content or right of a horizontal flow
     *         content), returns a <em>hit after cells</em> containing offset
     *         from the top right corner of the content of a horizontal flow or
     *         bottom left corner of the content of a vertical flow.
     */
    @Override
    public Hit<C> hit(double x, double y) {
        double lOff = y;

        if (items.isEmpty()) {
            return hitAfterCells(0, lOff);
        }

        layout();

        int firstVisible = cellPositioner.getFirstVisibleIndex()
                                         .getAsInt();
        firstVisible = navigator.fillBackwardFrom0(firstVisible, lOff);
        C firstCell = cellPositioner.getVisibleCell(firstVisible);

        int lastVisible = cellPositioner.getLastVisibleIndex()
                                        .getAsInt();
        lastVisible = navigator.fillForwardFrom0(lastVisible, lOff);
        C lastCell = cellPositioner.getVisibleCell(lastVisible);
        Node node2 = firstCell.getNode();

        if (lOff < node2.getLayoutY() + node2.getLayoutBounds()
                                             .getMinY()) {
            Node node = firstCell.getNode();
            return hitBeforeCells(0,
                                  lOff - (node.getLayoutY()
                                          + node.getLayoutBounds()
                                                .getMinY()));
        } else {
            Node node = lastCell.getNode();
            if (lOff >= node.getLayoutY() + node.getLayoutBounds()
                                                .getMinY()
                        + node.getLayoutBounds()
                              .getHeight()) {
                Node node1 = lastCell.getNode();
                return hitAfterCells(0,
                                     lOff - (node1.getLayoutY()
                                             + node1.getLayoutBounds()
                                                    .getMinY()
                                             + node1.getLayoutBounds()
                                                    .getHeight()));
            } else {
                for (int i = firstVisible; i <= lastVisible; ++i) {
                    C cell = cellPositioner.getVisibleCell(i);
                    Node node1 = cell.getNode();
                    if (lOff < node1.getLayoutY() + node1.getLayoutBounds()
                                                         .getMinY()
                               + node1.getLayoutBounds()
                                      .getHeight()) {
                        Node node3 = cell.getNode();
                        return cellHit(i, cell, 0,
                                       lOff - (node3.getLayoutY()
                                               + node3.getLayoutBounds()
                                                      .getMinY()));
                    }
                }
                throw new AssertionError("unreachable code");
            }
        }
    }

    public Var<Double> lengthOffsetEstimateProperty() {
        return lengthOffsetEstimate;
    }

    public void scrollDown() {
        scrollYBy(sizeTracker.getCellLength());
    }

    public void scrollUp() {
        scrollYBy(-sizeTracker.getCellLength());
    }

    /**
     * Scroll the content vertically by the given amount.
     *
     * @param deltaY
     *            positive value scrolls down, negative value scrolls up
     */
    public void scrollYBy(double deltaY) {
        this.scrollLength(deltaY);
    }

    /**
     * Scroll the content vertically to the pixel
     *
     * @param pixel
     *            - the pixel position to which to scroll
     */
    public void scrollYToPixel(double pixel) {
        this.setLengthOffset(pixel);
    }

    /**
     * Forces the viewport to acts as though it scrolled from 0 to
     * {@code viewportOffset}). <em>Note:</em> the viewport makes an educated
     * guess as to which cell is actually at {@code viewportOffset} if the
     * viewport's entire content was completely rendered.
     *
     * @param viewportOffset
     *            See {@link OrientationHelper} and its implementations for
     *            explanation on what the offset means based on which
     *            implementation is used.
     */
    public void show(double viewportOffset) {
        if (viewportOffset < 0) {
            navigator.scrollCurrentPositionBy(viewportOffset);
        } else if (viewportOffset > sizeTracker.getViewportLength()) {
            navigator.scrollCurrentPositionBy(viewportOffset
                                              - sizeTracker.getViewportLength());
        } else {
            // do nothing, offset already in the viewport
        }
    }

    /**
     * Forces the viewport to show the given item by "scrolling" to it
     */
    public void show(int itemIdx) {
        navigator.setTargetPosition(new MinDistanceTo(itemIdx));
    }

    /**
     * Forces the viewport to show the given item by "scrolling" to it and then
     * further "scrolling," so that the {@code region} is visible, in one layout
     * call (e.g., this method does not "scroll" twice).
     */
    public void show(int itemIndex, Bounds region) {
        navigator.showLengthRegion(itemIndex, region.getMinY(),
                                   region.getMinY() + region.getHeight());
    }

    /**
     * Forces the viewport to show the given item as the first visible item as
     * determined by its {@link Gravity}.
     */
    public void showAsFirst(int itemIdx) {
        navigator.setTargetPosition(new StartOffStart(itemIdx, 0.0));
    }

    /**
     * Forces the viewport to show the given item as the last visible item as
     * determined by its {@link Gravity}.
     */
    public void showAsLast(int itemIdx) {
        navigator.setTargetPosition(new EndOffEnd(itemIdx, 0.0));
    }

    /**
     * Forces the viewport to show the given item by "scrolling" to it and then
     * further "scrolling" by {@code offset} in one layout call (e.g., this
     * method does not "scroll" twice)
     *
     * @param offset
     *            the offset value as determined by the viewport's
     *            {@link OrientationHelper}.
     */
    public void showAtOffset(int itemIdx, double offset) {
        navigator.setTargetPosition(new StartOffStart(itemIdx, offset));
    }

    public Val<Double> totalBreadthEstimateProperty() {
        return sizeTracker.maxCellBreadthProperty();
    }

    public Val<Double> totalLengthEstimateProperty() {
        return sizeTracker.totalLengthEstimateProperty();
    }

    /**
     * This method calls {@link #layout()} as a side-effect to insure that the
     * VirtualFlow is up-to-date in light of any changes
     */
    public ObservableList<C> visibleCells() {
        // insure cells are up-to-date in light of any changes
        layout();
        return cellListManager.getLazyCellList()
                              .memoizedItems();
    }

    @Override
    protected final double computePrefHeight(double width) {
        switch (getContentBias()) {
            case HORIZONTAL: // vertical flow
                return computePrefLength(width);
            case VERTICAL: // horizontal flow
                return computePrefBreadth();
            default:
                throw new AssertionError("Unreachable code");
        }
    }

    @Override
    protected final double computePrefWidth(double height) {
        switch (getContentBias()) {
            case HORIZONTAL: // vertical flow
                return computePrefBreadth();
            case VERTICAL: // horizontal flow
                return computePrefLength(height);
            default:
                throw new AssertionError("Unreachable code");
        }
    }

    @Override
    protected void layoutChildren() {

        // navigate to the target position and fill viewport
        while (true) {
            double oldLayoutBreadth = sizeTracker.getCellLayoutBreadth();
            navigator.resize(oldLayoutBreadth, sizeTracker.getViewportLength());
            navigator.layout();
            if (oldLayoutBreadth == sizeTracker.getCellLayoutBreadth()) {
                break;
            }
        }
    }

    void scrollLength(double deltaLength) {
        setLengthOffset(lengthOffsetEstimate.getValue() + deltaLength);
    }

    void setLengthOffset(double pixels) {
        double total = totalLengthEstimateProperty().getOrElse(0.0);
        double length = sizeTracker.getViewportLength();
        double max = Math.max(total - length, 0);
        double current = lengthOffsetEstimate.getValue();

        if (pixels > max) {
            pixels = max;
        }
        if (pixels < 0) {
            pixels = 0;
        }

        double diff = pixels - current;
        if (diff == 0) {
            // do nothing
        } else if (Math.abs(diff) < length) { // distance less than one screen
            navigator.scrollCurrentPositionBy(diff);
        } else {
            jumpToAbsolutePosition(pixels);
        }
    }

    private double computePrefBreadth() {
        return sizeTracker.getCellLayoutBreadth();
    }

    private double computePrefLength(double breadth) {
        return sizeTracker.getCellLength();
    }

    private void jumpToAbsolutePosition(double pixels) {
        if (items.isEmpty()) {
            return;
        }

        // guess the first visible cell and its offset in the viewport
        double avgLen = sizeTracker.getAverageLengthEstimate()
                                   .orElse(0.0);
        if (avgLen == 0.0) {
            return;
        }
        int first = (int) Math.floor(pixels / avgLen);
        double firstOffset = -(pixels % avgLen);

        if (first < items.size()) {
            navigator.setTargetPosition(new StartOffStart(first, firstOffset));
        } else {
            navigator.setTargetPosition(new EndOffEnd(items.size() - 1, 0.0));
        }
    }
}
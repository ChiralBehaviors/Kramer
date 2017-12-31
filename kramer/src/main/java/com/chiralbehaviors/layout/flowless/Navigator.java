package com.chiralbehaviors.layout.flowless;

import java.util.Optional;
import java.util.OptionalInt;

import org.reactfx.Subscription;
import org.reactfx.collection.LiveList;
import org.reactfx.collection.MemoizationList;
import org.reactfx.collection.QuasiListChange;
import org.reactfx.collection.QuasiListModification;

import com.chiralbehaviors.layout.cell.LayoutCell;

import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * Responsible for laying out cells' nodes within the viewport based on a single
 * anchor node. In a layout call, this anchor node is positioned in the viewport
 * before any other node and then nodes are positioned above and below that
 * anchor node sequentially. This sequential layout continues until the
 * viewport's "top" and "bottom" edges are reached or there are no other cells'
 * nodes to render. In this latter case (when there is not enough content to
 * fill up the entire viewport), the displayed cells are repositioned towards
 * the "ground," based on the {@link VirtualFlow}'s {@link Gravity} value, and
 * any remaining unused space counts as the "sky."
 */
final class Navigator<C extends LayoutCell<?>> extends Region
        implements TargetPositionVisitor {
    private final CellListManager<C> cellListManager;
    private final MemoizationList<C> cells;
    private TargetPosition           currentPosition = TargetPosition.BEGINNING;
    private final Subscription       itemsSubscription;
    private final CellPositioner<C>  positioner;

    private final SizeTracker        sizeTracker;
    private TargetPosition           targetPosition  = TargetPosition.BEGINNING;

    public Navigator(CellListManager<C> cellListManager,
                     CellPositioner<C> positioner, SizeTracker sizeTracker) {
        this.cellListManager = cellListManager;
        this.cells = cellListManager.getLazyCellList();
        this.positioner = positioner;
        this.sizeTracker = sizeTracker;

        this.itemsSubscription = LiveList.observeQuasiChanges(cellListManager.getLazyCellList(),
                                                              this::itemsChanged);
        Bindings.bindContent(getChildren(), cellListManager.getNodes());
    }

    public void dispose() {
        itemsSubscription.unsubscribe();
        Bindings.unbindContent(getChildren(), cellListManager.getNodes());
    }

    /**
     * Sets the {@link TargetPosition} used to layout the anchor node to the
     * current position scrolled by {@code delta} and re-lays out the viewport
     */
    public void scrollCurrentPositionBy(double delta) {
        targetPosition = currentPosition.scrollBy(delta);
        requestLayout();
    }

    /**
     * Sets the {@link TargetPosition} used to layout the anchor node and
     * re-lays out the viewport
     */
    public void setTargetPosition(TargetPosition targetPosition) {
        this.targetPosition = targetPosition;
        requestLayout();
    }

    @Override
    public void visit(EndOffEnd targetPosition) {
        placeEndOffEndMayCrop(targetPosition.itemIndex,
                              targetPosition.offsetFromEnd);
        fillViewportFrom(targetPosition.itemIndex);
    }

    @Override
    public void visit(MinDistanceTo targetPosition) {
        Optional<C> cell = positioner.getCellIfVisible(targetPosition.itemIndex);
        if (cell.isPresent()) {
            placeToViewport(targetPosition.itemIndex, targetPosition.minY,
                            targetPosition.maxY);
        } else {
            OptionalInt prevVisible;
            OptionalInt nextVisible;
            if ((prevVisible = positioner.lastVisibleBefore(targetPosition.itemIndex)).isPresent()) {
                // Try keeping prevVisible in place:
                // fill the viewport, see if the target item appeared.
                fillForwardFrom(prevVisible.getAsInt());
                cell = positioner.getCellIfVisible(targetPosition.itemIndex);
                if (cell.isPresent()) {
                    placeToViewport(targetPosition.itemIndex,
                                    targetPosition.minY, targetPosition.maxY);
                } else if (targetPosition.maxY.isFromStart()) {
                    placeStartOffEndMayCrop(targetPosition.itemIndex,
                                            -targetPosition.maxY.getValue());
                } else {
                    placeEndOffEndMayCrop(targetPosition.itemIndex,
                                          -targetPosition.maxY.getValue());
                }
            } else if ((nextVisible = positioner.firstVisibleAfter(targetPosition.itemIndex
                                                                   + 1)).isPresent()) {
                // Try keeping nextVisible in place:
                // fill the viewport, see if the target item appeared.
                fillBackwardFrom(nextVisible.getAsInt());
                cell = positioner.getCellIfVisible(targetPosition.itemIndex);
                if (cell.isPresent()) {
                    placeToViewport(targetPosition.itemIndex,
                                    targetPosition.minY, targetPosition.maxY);
                } else if (targetPosition.minY.isFromStart()) {
                    placeStartAtMayCrop(targetPosition.itemIndex,
                                        -targetPosition.minY.getValue());
                } else {
                    placeEndOffStartMayCrop(targetPosition.itemIndex,
                                            -targetPosition.minY.getValue());
                }
            } else {
                if (targetPosition.minY.isFromStart()) {
                    placeStartAtMayCrop(targetPosition.itemIndex,
                                        -targetPosition.minY.getValue());
                } else {
                    placeEndOffStartMayCrop(targetPosition.itemIndex,
                                            -targetPosition.minY.getValue());
                }
            }
        }
        fillViewportFrom(targetPosition.itemIndex);
    }

    @Override
    public void visit(StartOffStart targetPosition) {
        placeStartAtMayCrop(targetPosition.itemIndex,
                            targetPosition.offsetFromStart);
        fillViewportFrom(targetPosition.itemIndex);
    }

    @Override
    protected void layoutChildren() {
        if (!cells.isEmpty()) {
            targetPosition.clamp(cells.size())
                          .accept(this);
        }
        currentPosition = getCurrentPosition();
        targetPosition = currentPosition;
    }

    // does not re-place the anchor cell
    int fillBackwardFrom0(int itemIndex, double upTo) {
        Node node = positioner.getVisibleCell(itemIndex)
                              .getNode();
        double min = node.getLayoutY() + node.getLayoutBounds()
                                             .getMinY();
        int i = itemIndex;
        while (min > upTo && i > 0) {
            --i;
            C c = positioner.placeEndFromStart(i, min);
            Node node1 = c.getNode();
            min = node1.getLayoutY() + node1.getLayoutBounds()
                                            .getMinY();
        }
        return i;
    }

    int fillForwardFrom0(int itemIndex, double upTo) {
        Node node = positioner.getVisibleCell(itemIndex)
                              .getNode();
        double max = node.getLayoutY() + node.getLayoutBounds()
                                             .getMinY()
                     + node.getLayoutBounds()
                           .getHeight();
        int i = itemIndex;
        while (max < upTo && i < cellListManager.getLazyCellList()
                                                .size()
                                 - 1) {
            ++i;
            C c = positioner.placeStartAt(i, max);
            Node node1 = c.getNode();
            max = node1.getLayoutY() + node1.getLayoutBounds()
                                            .getMinY()
                  + node1.getLayoutBounds()
                         .getHeight();
        }
        return i;
    }

    void showLengthRegion(int itemIndex, double fromY, double toY) {
        setTargetPosition(new MinDistanceTo(itemIndex, Offset.fromStart(fromY),
                                            Offset.fromStart(toY)));
    }

    private void cropToNeighborhoodOf(int itemIndex, double additionalOffset) {
        double spaceBefore = Math.max(0, sizeTracker.getViewportLength()
                                         + additionalOffset);
        double spaceAfter = Math.max(0, sizeTracker.getViewportLength()
                                        - additionalOffset);

        Optional<Double> avgLen = sizeTracker.getAverageLengthEstimate();
        int itemsBefore = avgLen.map(l -> spaceBefore / l)
                                .orElse(5.0)
                                .intValue();
        int itemsAfter = avgLen.map(l -> spaceAfter / l)
                               .orElse(5.0)
                               .intValue();

        positioner.cropTo(itemIndex - itemsBefore, itemIndex + 1 + itemsAfter);
    }

    private double distanceFromGround(int itemIndex) {
        C cell = positioner.getVisibleCell(itemIndex);
        Node node = cell.getNode();
        return node.getLayoutY() + node.getLayoutBounds()
                                       .getMinY();
    }

    private double distanceFromSky(int itemIndex) {
        C cell = positioner.getVisibleCell(itemIndex);
        Node node = cell.getNode();
        return sizeTracker.getViewportLength() - (node.getLayoutY()
                                                  + node.getLayoutBounds()
                                                        .getMinY()
                                                  + node.getLayoutBounds()
                                                        .getHeight());
    }

    private int fillBackwardFrom(int itemIndex) {
        return fillBackwardFrom(itemIndex, 0.0);
    }

    private int fillBackwardFrom(int itemIndex, double upTo) {
        // resize and/or reposition the starting cell
        // in case the preferred or available size changed
        C cell = positioner.getVisibleCell(itemIndex);
        Node node = cell.getNode();
        double length0 = node.getLayoutY() + node.getLayoutBounds()
                                                 .getMinY();
        positioner.placeStartAt(itemIndex, length0);

        return fillBackwardFrom0(itemIndex, upTo);
    }

    private int fillBackwardFrom0(int itemIndex) {
        return fillBackwardFrom0(itemIndex, 0.0);
    }

    private int fillForwardFrom(int itemIndex) {
        return fillForwardFrom(itemIndex, sizeTracker.getViewportLength());
    }

    private int fillForwardFrom(int itemIndex, double upTo) {
        // resize and/or reposition the starting cell
        // in case the preferred or available size changed
        C cell = positioner.getVisibleCell(itemIndex);
        Node node = cell.getNode();
        double length0 = node.getLayoutY() + node.getLayoutBounds()
                                                 .getMinY();
        positioner.placeStartAt(itemIndex, length0);

        return fillForwardFrom0(itemIndex, upTo);
    }

    private int fillForwardFrom0(int itemIndex) {
        return fillForwardFrom0(itemIndex, sizeTracker.getViewportLength());
    }

    private int fillTowardsGroundFrom0(int itemIndex) {
        return fillBackwardFrom0(itemIndex);
    }

    private int fillTowardsGroundFrom0(int itemIndex, double upTo) {
        return fillBackwardFrom0(itemIndex, upTo);
    }

    private int fillTowardsSkyFrom0(int itemIndex) {
        return fillForwardFrom0(itemIndex);
    }

    /**
     * Starting from the anchor cell's node, fills the viewport from the anchor
     * to the "ground" and then from the anchor to the "sky".
     *
     * @param itemIndex
     *            the index of the anchor cell
     */
    private void fillViewportFrom(int itemIndex) {
        // cell for itemIndex is assumed to be placed correctly

        // fill up to the ground
        int ground = fillTowardsGroundFrom0(itemIndex);

        // if ground not reached, shift cells to the ground
        double gapBefore = distanceFromGround(ground);
        if (gapBefore > 0) {
            shiftCellsTowardsGround(ground, itemIndex, gapBefore);
        }

        // fill up to the sky
        int sky = fillTowardsSkyFrom0(itemIndex);

        // if sky not reached, add more cells under the ground and then shift
        double gapAfter = distanceFromSky(sky);
        if (gapAfter > 0) {
            ground = fillTowardsGroundFrom0(ground, -gapAfter);
            double extraBefore = -distanceFromGround(ground);
            double shift = Math.min(gapAfter, extraBefore);
            shiftCellsTowardsGround(ground, sky, -shift);
        }

        // crop to the visible cells
        int first = Math.min(ground, sky);
        int last = Math.max(ground, sky);
        Node node = positioner.getVisibleCell(first)
                              .getNode();
        while (first < last && node.getLayoutY() + node.getLayoutBounds()
                                                       .getMinY()
                               + node.getLayoutBounds()
                                     .getHeight() <= 0.0) {
            ++first;
        }
        Node node1 = positioner.getVisibleCell(last)
                               .getNode();
        while (last > first && node1.getLayoutY() + node1.getLayoutBounds()
                                                         .getMinY() >= sizeTracker.getViewportLength()) {
            --last;
        }
        positioner.cropTo(first, last + 1);
    }

    private TargetPosition getCurrentPosition() {
        OptionalInt firstVisible = positioner.getFirstVisibleIndex();
        if (firstVisible.isPresent()) {
            int idx = firstVisible.getAsInt();
            C cell = positioner.getVisibleCell(idx);
            Node node = cell.getNode();
            return new StartOffStart(idx,
                                     node.getLayoutY() + node.getLayoutBounds()
                                                             .getMinY());
        } else {
            return TargetPosition.BEGINNING;
        }
    }

    private void itemsChanged(QuasiListChange<?> ch) {
        for (QuasiListModification<?> mod : ch) {
            targetPosition = targetPosition.transformByChange(mod.getFrom(),
                                                              mod.getRemovedSize(),
                                                              mod.getAddedSize());
        }
        requestLayout(); // TODO: could optimize to only request layout if
                         // target position changed or cells in the viewport
                         // are affected
    }

    private void placeEndOffEndMayCrop(int itemIndex, double endOffEnd) {
        cropToNeighborhoodOf(itemIndex, endOffEnd);
        positioner.placeEndFromEnd(itemIndex, endOffEnd);
    }

    private void placeEndOffStartMayCrop(int itemIndex, double endOffStart) {
        cropToNeighborhoodOf(itemIndex, endOffStart);
        positioner.placeEndFromStart(itemIndex, endOffStart);
    }

    private void placeStartAtMayCrop(int itemIndex, double startOffStart) {
        cropToNeighborhoodOf(itemIndex, startOffStart);
        positioner.placeStartAt(itemIndex, startOffStart);
    }

    private void placeStartOffEndMayCrop(int itemIndex, double startOffEnd) {
        cropToNeighborhoodOf(itemIndex, startOffEnd);
        positioner.placeStartFromEnd(itemIndex, startOffEnd);
    }

    private void placeToViewport(int itemIndex, double fromY, double toY) {
        C cell = positioner.getVisibleCell(itemIndex);
        double d = positioner.shortestDeltaToViewport(cell, fromY, toY);
        Node node = cell.getNode();
        positioner.placeStartAt(itemIndex,
                                node.getLayoutY() + node.getLayoutBounds()
                                                        .getMinY()
                                           + d);
    }

    private void placeToViewport(int itemIndex, Offset from, Offset to) {
        C cell = positioner.getVisibleCell(itemIndex);
        double fromY = from.isFromStart() ? from.getValue()
                                          : ((Node) cell.getNode()).getLayoutBounds()
                                                                   .getHeight()
                                            + to.getValue();
        double toY = to.isFromStart() ? to.getValue()
                                      : ((Node) cell.getNode()).getLayoutBounds()
                                                               .getHeight()
                                        + to.getValue();
        placeToViewport(itemIndex, fromY, toY);
    }

    private void shiftCellsTowardsGround(int groundCellIndex, int lastCellIndex,
                                         double amount) {
        assert groundCellIndex <= lastCellIndex;
        for (int i = groundCellIndex; i <= lastCellIndex; ++i) {
            positioner.shiftCellBy(positioner.getVisibleCell(i), -amount);
        }
    }
}
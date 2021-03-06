package com.chiralbehaviors.layout.flowless;

import java.util.Optional;
import java.util.function.Function;

import org.reactfx.Subscription;
import org.reactfx.collection.LiveList;
import org.reactfx.collection.MemoizationList;
import org.reactfx.value.Val;
import org.reactfx.value.ValBase;
import org.reactfx.value.Var;

import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Bounds;
import javafx.scene.control.IndexRange;

/**
 * Estimates the size of the entire viewport (if it was actually completely
 * rendered) based on the known sizes of the {@link Cell}s whose nodes are
 * currently displayed in the viewport and an estimated average of {@link Cell}s
 * whose nodes are not displayed in the viewport. The meaning of
 * {@link #breadthForCells} and {@link #totalLengthEstimate} are dependent upon
 * which implementation of {@link OrientationHelper} is used.
 */
final class SizeTracker {
    private static <T> Val<T> avoidFalseInvalidations(Val<T> src) {
        return new ValBase<T>() {
            @Override
            protected T computeValue() {
                return src.getValue();
            }

            @Override
            protected Subscription connect() {
                return src.observeChanges((obs, oldVal,
                                           newVal) -> invalidate());
            }
        };
    }

    /**
     * Stores either null or the average length of the cells' nodes currently
     * displayed in the viewport
     */
    private final Val<Double>                           averageLengthEstimate;
    /**
     * Stores either the greatest minimum cell's node's breadth or the
     * viewport's breadth
     */
    private final Val<Double>                           breadthForCells;
    private final MemoizationList<? extends Cell<?, ?>> cells;

    private final Val<Double>                           lengthOffsetEstimate;

    private final MemoizationList<Double>               lengths;

    private final Val<Double>                           maxKnownMinBreadth;

    private final Subscription                          subscription;

    private final Val<Double>                           totalLengthEstimate;
    private final ObservableObjectValue<Bounds>         viewportBounds;
    private double                                      width, height;

    /**
     * Constructs a SizeTracker
     */
    public SizeTracker(double breadth, double length,
                       ObservableObjectValue<Bounds> viewportBounds,
                       MemoizationList<? extends Cell<?, ?>> lazyCells) {
        this.width = breadth;
        this.height = length;
        this.viewportBounds = viewportBounds;
        this.cells = lazyCells;
        this.maxKnownMinBreadth = Var.newSimpleVar(width);
        this.breadthForCells = Val.combine(maxKnownMinBreadth, viewportBounds,
                                           (a, b) -> Math.max(a, b.getWidth()));

        Val<Function<Cell<?, ?>, Double>> lengthFn = avoidFalseInvalidations(breadthForCells).map(m -> cell -> height);

        this.lengths = cells.mapDynamic(lengthFn)
                            .memoize();

        LiveList<Double> knownLengths = this.lengths.memoizedItems();

        this.averageLengthEstimate = Val.constant(height);

        this.totalLengthEstimate = Val.constant(height * cells.size());

        Val<Integer> firstVisibleIndex = Val.create(() -> cells.getMemoizedCount() == 0 ? null
                                                                                        : cells.indexOfMemoizedItem(0),
                                                    cells,
                                                    cells.memoizedItems());
        // need to observe cells.memoizedItems()
        // as well, because they may change without a change in cells.

        Val<? extends Cell<?, ?>> firstVisibleCell = cells.memoizedItems()
                                                          .collapse(visCells -> visCells.isEmpty() ? null
                                                                                                   : visCells.get(0));

        Val<Integer> knownLengthCountBeforeFirstVisibleCell = Val.create(() -> {
            return firstVisibleIndex.getOpt()
                                    .map(i -> lengths.getMemoizedCountBefore(Math.min(i,
                                                                                      lengths.size())))
                                    .orElse(0);
        }, lengths, firstVisibleIndex);

        Val<Double> totalKnownLengthBeforeFirstVisibleCell = knownLengths.reduceRange(knownLengthCountBeforeFirstVisibleCell.map(n -> new IndexRange(0,
                                                                                                                                                     n)),
                                                                                      (a,
                                                                                       b) -> a
                                                                                             + b)
                                                                         .orElseConst(0.0);

        Val<Double> unknownLengthEstimateBeforeFirstVisibleCell = Val.combine(firstVisibleIndex,
                                                                              knownLengthCountBeforeFirstVisibleCell,
                                                                              averageLengthEstimate,
                                                                              (firstIdx,
                                                                               knownCnt,
                                                                               avgLen) -> (firstIdx
                                                                                           - knownCnt)
                                                                                          * avgLen);

        Val<Double> firstCellMinY = firstVisibleCell.flatMap(node -> Val.combine(node.getNode()
                                                                                     .layoutYProperty(),
                                                                                 node.getNode()
                                                                                     .layoutBoundsProperty(),
                                                                                 (layoutY,
                                                                                  layoutBounds) -> layoutY.doubleValue()
                                                                                                   + layoutBounds.getMinY()));

        lengthOffsetEstimate = Val.combine(totalKnownLengthBeforeFirstVisibleCell,
                                           unknownLengthEstimateBeforeFirstVisibleCell,
                                           firstCellMinY,
                                           (a, b, minY) -> a + b - minY)
                                  .orElseConst(0.0);

        // pinning totalLengthEstimate and lengthOffsetEstimate
        // binds it all together and enables memoization
        this.subscription = Subscription.multi(() -> totalLengthEstimate.getValue(),
                                               () -> lengthOffsetEstimate.getValue());
    }

    public Val<Double> averageLengthEstimateProperty() {
        return averageLengthEstimate;
    }

    public double breadthFor(int itemIndex) {
        return width;
    }

    public void dispose() {
        subscription.unsubscribe();
    }

    public Optional<Double> getAverageLengthEstimate() {
        return averageLengthEstimate.getOpt();
    }

    public double getCellLayoutBreadth() {
        return breadthForCells.getValue();
    }

    public double getCellLength() {
        return height;
    }

    public double getViewportBreadth() {
        return viewportBounds.get()
                             .getWidth();
    }

    public double getViewportLength() {
        return viewportBounds.get()
                             .getHeight();
    }

    public double lengthFor(int itemIndex) {
        return lengths.get(itemIndex);
    }

    public Val<Double> lengthOffsetEstimateProperty() {
        return lengthOffsetEstimate;
    }

    public Val<Double> maxCellBreadthProperty() {
        return maxKnownMinBreadth;
    }

    public Val<Double> totalLengthEstimateProperty() {
        return totalLengthEstimate;
    }
}
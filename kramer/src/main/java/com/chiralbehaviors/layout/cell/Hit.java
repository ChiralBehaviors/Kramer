package com.chiralbehaviors.layout.cell;

import com.chiralbehaviors.layout.flowless.VirtualFlow;

import javafx.geometry.Point2D;

/**
 * Stores the result of a {@link VirtualFlow#hit(double, double)}. Before
 * calling any of the getters, one should determine what kind of hit this object
 * is via {@link #isCellHit()}, {@link #isBeforeCells()}, and
 * {@link #isAfterCells()}. Otherwise, calling the wrong getter will throw an
 * {@link UnsupportedOperationException}.
 *
 * <p>
 * Types of VirtualFlowHit:
 * </p>
 * <ul>
 * <li><em>Cell Hit:</em> a hit occurs on a displayed cell's node. One can call
 * {@link #getCell()}, {@link #getCellIndex()}, and {@link #getCellOffset()}.
 * </li>
 * <li><em>Hit Before Cells:</em> a hit occurred before the displayed cells. One
 * can call {@link #getOffsetBeforeCells()}.</li>
 * <li><em>Hit After Cells:</em> a hit occurred after the displayed cells. One
 * can call {@link #getOffsetAfterCells()}.</li>
 * </ul>
 *
 */
public abstract class Hit<C extends LayoutCell<?>> {

    public Hit() {
    }

    public abstract C getCell();

    public abstract int getCellIndex();

    public abstract Point2D getCellOffset();

    public abstract Point2D getOffsetAfterCells();

    public abstract Point2D getOffsetBeforeCells();

    public abstract boolean isAfterCells();

    public abstract boolean isBeforeCells();

    public abstract boolean isCellHit();
}
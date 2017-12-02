package com.chiralbehaviors.layout.flowless;

import static javafx.scene.control.SelectionMode.SINGLE;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.reactfx.collection.MemoizationList;
import org.reactfx.util.Lists;
import org.reactfx.value.Val;
import org.reactfx.value.Var;

import com.chiralbehaviors.layout.cell.FocusTraversal;
import com.chiralbehaviors.layout.cell.MouseHandler;
import com.sun.javafx.collections.MappingChange;
import com.sun.javafx.collections.NonIterableChange;
import com.sun.javafx.scene.control.ReadOnlyUnbackedObservableList;

import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 * A VirtualFlow is a memory-efficient viewport that only renders enough of its
 * content to completely fill up the viewport through its {@link Navigator}.
 * Based on the viewport's {@link Gravity}, it sequentially lays out the
 * {@link javafx.scene.Node}s of the {@link Cell}s until the viewport is
 * completely filled up or it has no additional cell's nodes to render.
 *
 * <p>
 * Since this viewport does not fully render all of its content, the scroll
 * values are estimates based on the nodes that are currently displayed in the
 * viewport. If every node that could be rendered is the same width or same
 * height, then the corresponding scroll values (e.g., scrollX or totalX) are
 * accurate. <em>Note:</em> the VirtualFlow does not have scroll bars by
 * default. These can be added by wrapping this object in a
 * {@link VirtualizedScrollPane}.
 * </p>
 *
 * <p>
 * Since the viewport can be used to lay out its content horizontally or
 * vertically, it uses two orientation-agnostic terms to refer to its width and
 * height: "breadth" and "length," respectively. The viewport always lays out
 * its {@link Cell cell}'s {@link javafx.scene.Node}s from "top-to-bottom" or
 * from "bottom-to-top" (these terms should be understood in reference to the
 * viewport's {@link OrientationHelper orientation} and {@link Gravity}). Thus,
 * its length ("height") is independent as the viewport's bounds are dependent
 * upon its parent's bounds whereas its breadth ("width") is dependent upon its
 * length.
 * </p>
 *
 * @param <T>
 *            the model content that the {@link Cell#getNode() cell's node}
 *            renders
 * @param <C>
 *            the {@link Cell} that can render the model with a
 *            {@link javafx.scene.Node}.
 */
@SuppressWarnings("restriction")
public class VirtualFlow<T, C extends Cell<T, ?>> extends Region
        implements Virtualized {

    /**
     * Determines how the cells in the viewport should be laid out and where any
     * extra unused space should exist if there are not enough cells to
     * completely fill up the viewport
     */
    public static enum Gravity {
        /**
         * If using a {@link VerticalHelper vertical viewport}, lays out the
         * content from top-to-bottom. The first visible item will appear at the
         * top and the last visible item (or unused space) towards the bottom.
         * <p>
         * If using a {@link HorizontalHelper horizontal viewport}, lays out the
         * content from left-to-right. The first visible item will appear at the
         * left and the last visible item (or unused space) towards the right.
         * </p>
         */
        FRONT,
        /**
         * If using a {@link VerticalHelper vertical viewport}, lays out the
         * content from bottom-to-top. The first visible item will appear at the
         * bottom and the last visible item (or unused space) towards the top.
         * <p>
         * If using a {@link HorizontalHelper horizontal viewport}, lays out the
         * content from right-to-left. The first visible item will appear at the
         * right and the last visible item (or unused space) towards the left.
         * </p>
         */
        REAR
    }

    public class VirtualFlowSelectionModel extends MultipleSelectionModel<T> {
        final BitSet                                    selectedIndices;
        final ReadOnlyUnbackedObservableList<Integer>   selectedIndicesSeq;
        ListChangeListener.Change<T>                    selectedItemChange;
        private int                                     atomicityCount = 0;
        private final ReadOnlyUnbackedObservableList<T> selectedItemsSeq;
        private int                                     focusedIndex   = -1;

        public VirtualFlowSelectionModel() {
            selectedIndexProperty().addListener(valueModel -> {
                setSelectedItem(getModelItem(getSelectedIndex()));
            });

            selectedIndices = new BitSet();

            selectedIndicesSeq = createListFromBitSet(selectedIndices);

            final MappingChange.Map<Integer, T> map = f -> getModelItem(f);

            selectedItemsSeq = new ReadOnlyUnbackedObservableList<T>() {
                @Override
                public T get(int i) {
                    int pos = selectedIndicesSeq.get(i);
                    return getModelItem(pos);
                }

                @Override
                public int size() {
                    return selectedIndices.cardinality();
                }
            };

            selectedIndicesSeq.addListener((ListChangeListener<Integer>) c -> {
                // when the selectedIndices ObservableList changes, we manually call
                // the observers of the selectedItems ObservableList.

                // Fix for a bug identified whilst fixing RT-37395:
                // We shouldn't fire events on the selectedItems list unless
                // the indices list has actually changed. This means that index
                // permutation events should not be forwarded blindly through the
                // items list, as a index permutation implies the items list is
                // unchanged, not changed!
                boolean hasRealChangeOccurred = false;
                while (c.next() && !hasRealChangeOccurred) {
                    hasRealChangeOccurred = c.wasAdded() || c.wasRemoved();
                }

                if (hasRealChangeOccurred) {
                    if (selectedItemChange != null) {
                        selectedItemsSeq.callObservers(selectedItemChange);
                    } else {
                        c.reset();
                        selectedItemsSeq.callObservers(new MappingChange<Integer, T>(c,
                                                                                     map,
                                                                                     selectedItemsSeq));
                    }
                }
                c.reset();
            });
        }

        @Override
        public void clearAndSelect(int row) {
            if (row < 0 || row >= getItemCount()) {
                clearSelection();
                return;
            }

            final boolean wasSelected = isSelected(row);

            // RT-33558 if this method has been called with a given row, and that
            // row is the only selected row currently, then this method becomes a no-op.
            if (wasSelected && getSelectedIndices().size() == 1) {
                // before we return, we double-check that the selected item
                // is equal to the item in the given index
                if (getSelectedItem() == getModelItem(row)) {
                    return;
                }
            }

            // firstly we make a copy of the selection, so that we can send out
            // the correct details in the selection change event.
            // We remove the new selection from the list seeing as it is not removed.
            BitSet selectedIndicesCopy = new BitSet();
            selectedIndicesCopy.or(selectedIndices);
            selectedIndicesCopy.clear(row);
            List<Integer> previousSelectedIndices = createListFromBitSet(selectedIndicesCopy);

            // RT-32411 We used to call quietClearSelection() here, but this
            // resulted in the selectedItems and selectedIndices lists never
            // reporting that they were empty.
            // makeAtomic toggle added to resolve RT-32618
            startAtomic();

            // then clear the current selection
            clearSelection();

            // and select the new row
            select(row);
            stopAtomic();

            // fire off a single add/remove/replace notification (rather than
            // individual remove and add notifications) - see RT-33324
            ListChangeListener.Change<Integer> change;

            /*
             * getFrom() documentation:
             *   If wasAdded is true, the interval contains all the values that were added.
             *   If wasPermutated is true, the interval marks the values that were permutated.
             *   If wasRemoved is true and wasAdded is false, getFrom() and getTo() should
             *   return the same number - the place where the removed elements were positioned in the list.
             */
            if (wasSelected) {
                change = buildClearAndSelectChange(selectedIndicesSeq,
                                                   previousSelectedIndices,
                                                   row);
            } else {
                int changeIndex = selectedIndicesSeq.indexOf(row);
                change = new NonIterableChange.GenericAddRemoveChange<>(changeIndex,
                                                                        changeIndex + 1,
                                                                        previousSelectedIndices,
                                                                        selectedIndicesSeq);
            }

            selectedIndicesSeq.callObservers(change);
        }

        @Override
        public void clearSelection() {
            List<Integer> removed = createListFromBitSet((BitSet) selectedIndices.clone());

            quietClearSelection();

            if (!isAtomic()) {
                setSelectedIndex(-1);
                focus(-1);
                selectedIndicesSeq.callObservers(new NonIterableChange.GenericAddRemoveChange<>(0,
                                                                                                0,
                                                                                                removed,
                                                                                                selectedIndicesSeq));
            }
        }

        @Override
        public void clearSelection(int index) {
            if (index < 0) {
                return;
            }

            // TODO shouldn't directly access like this
            // TODO might need to update focus and / or selected index/item
            boolean wasEmpty = selectedIndices.isEmpty();
            selectedIndices.clear(index);

            if (!wasEmpty && selectedIndices.isEmpty()) {
                clearSelection();
            }

            if (!isAtomic()) {
                // we pass in (index, index) here to represent that nothing was added
                // in this change.
                selectedIndicesSeq.callObservers(new NonIterableChange.GenericAddRemoveChange<>(index,
                                                                                                index,
                                                                                                Collections.singletonList(index),
                                                                                                selectedIndicesSeq));
            }
        }

        public int getFocusedIndex() {
            return focusedIndex;
        }

        @Override
        public ObservableList<Integer> getSelectedIndices() {
            return selectedIndicesSeq;
        }

        @Override
        public ObservableList<T> getSelectedItems() {
            return selectedItemsSeq;
        }

        /***********************************************************************
         * * Public selection API * *
         **********************************************************************/

        @Override
        public boolean isEmpty() {
            return selectedIndices.isEmpty();
        }

        @Override
        public boolean isSelected(int index) {
            // Note the change in semantics here - we used to check to ensure that
            // the index is less than the item count, but now simply ensure that
            // it is less than the length of the selectedIndices bitset. This helps
            // to resolve issues such as RT-26721, where isSelected(int) was being
            // called for indices that exceeded the item count, as a TreeItem (e.g.
            // the root) was being collapsed.
            //            if (index >= 0 && index < getItemCount()) {
            if (index >= 0 && index < selectedIndices.length()) {
                return selectedIndices.get(index);
            }

            return false;
        }

        @Override
        public void select(int row) {
            if (row == -1) {
                clearSelection();
                return;
            }
            if (row < 0 || row >= getItemCount()) {
                return;
            }

            boolean isSameRow = row == getSelectedIndex();
            T currentItem = getSelectedItem();
            T newItem = getModelItem(row);
            boolean isSameItem = newItem != null && newItem.equals(currentItem);
            boolean fireUpdatedItemEvent = isSameRow && !isSameItem;

            startAtomic();
            if (!selectedIndices.get(row)) {
                if (getSelectionMode() == SINGLE) {
                    quietClearSelection();
                }
                selectedIndices.set(row);
            }

            setSelectedIndex(row);
            focus(row);

            stopAtomic();

            if (!isAtomic()) {
                int changeIndex = Math.max(0, selectedIndicesSeq.indexOf(row));
                selectedIndicesSeq.callObservers(new NonIterableChange.SimpleAddChange<Integer>(changeIndex,
                                                                                                changeIndex + 1,
                                                                                                selectedIndicesSeq));
            }

            if (fireUpdatedItemEvent) {
                setSelectedItem(newItem);
            }
        }

        @Override
        public void select(T obj) {
            //            if (getItemCount() <= 0) return;

            if (obj == null && getSelectionMode() == SelectionMode.SINGLE) {
                clearSelection();
                return;
            }

            // We have no option but to iterate through the model and select the
            // first occurrence of the given object. Once we find the first one, we
            // don't proceed to select any others.
            Object rowObj = null;
            for (int i = 0, max = getItemCount(); i < max; i++) {
                rowObj = getModelItem(i);
                if (rowObj == null) {
                    continue;
                }

                if (rowObj.equals(obj)) {
                    if (isSelected(i)) {
                        return;
                    }

                    if (getSelectionMode() == SINGLE) {
                        quietClearSelection();
                    }

                    select(i);
                    return;
                }
            }

            // if we are here, we did not find the item in the entire data model.
            // Even still, we allow for this item to be set to the give object.
            // We expect that in concrete subclasses of this class we observe the
            // data model such that we check to see if the given item exists in it,
            // whilst SelectedIndex == -1 && SelectedItem != null.
            setSelectedIndex(-1);
            setSelectedItem(obj);
        }

        @Override
        public void selectAll() {
            if (getSelectionMode() == SINGLE) {
                return;
            }

            if (getItemCount() <= 0) {
                return;
            }

            final int rowCount = getItemCount();
            final int focusedIndex = getFocusedIndex();

            // set all selected indices to true
            clearSelection();
            selectedIndices.set(0, rowCount, true);
            selectedIndicesSeq.callObservers(new NonIterableChange.SimpleAddChange<>(0,
                                                                                     rowCount,
                                                                                     selectedIndicesSeq));

            if (focusedIndex == -1) {
                setSelectedIndex(rowCount - 1);
                focus(rowCount - 1);
            } else {
                setSelectedIndex(focusedIndex);
                focus(focusedIndex);
            }
        }

        @Override
        public void selectFirst() {
            if (getSelectionMode() == SINGLE) {
                quietClearSelection();
            }

            if (getItemCount() > 0) {
                select(0);
            }
        }

        @Override
        public void selectIndices(int row, int... rows) {
            if (rows == null || rows.length == 0) {
                select(row);
                return;
            }

            /*
             * Performance optimisation - if multiple selection is disabled, only
             * process the end-most row index.
             */

            int rowCount = getItemCount();

            if (getSelectionMode() == SINGLE) {
                quietClearSelection();

                for (int i = rows.length - 1; i >= 0; i--) {
                    int index = rows[i];
                    if (index >= 0 && index < rowCount) {
                        selectedIndices.set(index);
                        select(index);
                        break;
                    }
                }

                if (selectedIndices.isEmpty()) {
                    if (row > 0 && row < rowCount) {
                        selectedIndices.set(row);
                        select(row);
                    }
                }

                selectedIndicesSeq.callObservers(new NonIterableChange.SimpleAddChange<Integer>(0,
                                                                                                1,
                                                                                                selectedIndicesSeq));
            } else {
                final List<Integer> actualSelectedRows = new ArrayList<Integer>();

                int lastIndex = -1;
                if (row >= 0 && row < rowCount) {
                    lastIndex = row;
                    if (!selectedIndices.get(row)) {
                        selectedIndices.set(row);
                        actualSelectedRows.add(row);
                    }
                }

                for (int row2 : rows) {
                    int index = row2;
                    if (index < 0 || index >= rowCount) {
                        continue;
                    }
                    lastIndex = index;

                    if (!selectedIndices.get(index)) {
                        selectedIndices.set(index);
                        actualSelectedRows.add(index);
                    }
                }

                if (lastIndex != -1) {
                    setSelectedIndex(lastIndex);
                    focus(lastIndex);
                    setSelectedItem(getModelItem(lastIndex));
                }

                // need to come up with ranges based on the actualSelectedRows, and
                // then fire the appropriate number of changes. We also need to
                // translate from a desired row to select to where that row is
                // represented in the selectedIndices list. For example,
                // we may have requested to select row 5, and the selectedIndices
                // list may therefore have the following: [1,4,5], meaning row 5
                // is in position 2 of the selectedIndices list
                Collections.sort(actualSelectedRows);
                Change<Integer> change = createRangeChange(selectedIndicesSeq,
                                                           actualSelectedRows,
                                                           false);
                selectedIndicesSeq.callObservers(change);
            }
        }

        @Override
        public void selectLast() {
            if (getSelectionMode() == SINGLE) {
                quietClearSelection();
            }

            int numItems = getItemCount();
            if (numItems > 0 && getSelectedIndex() < numItems - 1) {
                select(numItems - 1);
            }
        }

        @Override
        public void selectNext() {
            int focusIndex = getFocusedIndex();

            if (getSelectionMode() == SINGLE) {
                quietClearSelection();
            }

            if (focusIndex == -1) {
                select(0);
            } else if (focusIndex != getItemCount() - 1) {
                select(focusIndex + 1);
            }
        }

        @Override
        public void selectPrevious() {
            int focusIndex = getFocusedIndex();

            if (getSelectionMode() == SINGLE) {
                quietClearSelection();
            }

            if (focusIndex == -1) {
                select(getItemCount() - 1);
            } else if (focusIndex > 0) {
                select(focusIndex - 1);
            }
        }

        public void focus(int index) {
            VirtualFlow.this.notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_ITEM);
            C cell = getCell(index);
            cell.getNode()
                .requestFocus();
            focusedIndex = index;

        }

        /**
         * Returns the number of items in the data model that underpins the
         * control. An example would be that a ListView selection model would
         * likely return <code>listView.getItems().size()</code>. The valid
         * range of selectable indices is between 0 and whatever is returned by
         * this method.
         */
        protected int getItemCount() {
            return items.size();
        }

        /**
         * Returns the item at the given index. An example using ListView would
         * be <code>listView.getItems().get(index)</code>.
         *
         * @param index
         *            The index of the item that is requested from the
         *            underlying data model.
         * @return Returns null if the index is out of bounds, or an element of
         *         type T that is related to the given index.
         */
        protected T getModelItem(int index) {
            return items.get(index);
        }

        Change<Integer> createRangeChange(final ObservableList<Integer> list,
                                          final List<Integer> addedItems,
                                          boolean splitChanges) {
            Change<Integer> change = new Change<Integer>(list) {
                private final int   addedSize  = addedItems.size();
                private final int[] EMPTY_PERM = new int[0];

                private int         from;
                private boolean     invalid    = true;
                private int         pos        = 0;
                private int         to         = pos;

                {
                    from = pos;
                }

                @Override
                public int getAddedSize() {
                    return to - from;
                }

                @Override
                public int getFrom() {
                    checkState();
                    return from;
                }

                @Override
                public List<Integer> getRemoved() {
                    checkState();
                    return Collections.<Integer> emptyList();
                }

                @Override
                public int getTo() {
                    checkState();
                    return to;
                }

                @Override
                public boolean next() {
                    if (pos >= addedSize) {
                        return false;
                    }

                    // starting from pos, we keep going until the value is
                    // not the next value
                    int startValue = addedItems.get(pos++);
                    from = list.indexOf(startValue);
                    to = from + 1;
                    int endValue = startValue;
                    while (pos < addedSize) {
                        int previousEndValue = endValue;
                        endValue = addedItems.get(pos++);
                        ++to;
                        if (splitChanges
                            && previousEndValue != (endValue - 1)) {
                            break;
                        }
                    }

                    if (invalid) {
                        invalid = false;
                        return true;
                    }

                    // we keep going until we've represented all changes!
                    return splitChanges && pos < addedSize;
                }

                @Override
                public void reset() {
                    invalid = true;
                    pos = 0;
                    to = 0;
                    from = 0;
                }

                @Override
                protected int[] getPermutation() {
                    checkState();
                    return EMPTY_PERM;
                }

                private void checkState() {
                    if (invalid) {
                        throw new IllegalStateException("Invalid Change state: next() must be called before inspecting the Change.");
                    }
                }

            };
            return change;
        }

        boolean isAtomic() {
            return atomicityCount > 0;
        }

        void startAtomic() {
            atomicityCount++;
        }

        void stopAtomic() {
            atomicityCount = Math.max(0, --atomicityCount);
        }

        /***********************************************************************
         * * Private implementation * *
         **********************************************************************/

        private ReadOnlyUnbackedObservableList<Integer> createListFromBitSet(final BitSet bitset) {
            return new ReadOnlyUnbackedObservableList<Integer>() {
                private int lastGetIndex = -1;
                private int lastGetValue = -1;

                @Override
                public boolean contains(Object o) {
                    if (o instanceof Number) {
                        Number n = (Number) o;
                        int index = n.intValue();

                        return index >= 0 && index < bitset.length()
                               && bitset.get(index);
                    }

                    return false;
                }

                @Override
                public Integer get(int index) {
                    final int itemCount = getItemCount();
                    if (index < 0 || index >= itemCount) {
                        return -1;
                    }

                    if (index == (lastGetIndex + 1)
                        && lastGetValue < itemCount) {
                        // we're iterating forward in order, short circuit for
                        // performance reasons (RT-39776)
                        lastGetIndex++;
                        lastGetValue = bitset.nextSetBit(lastGetValue + 1);
                        return lastGetValue;
                    } else if (index == (lastGetIndex - 1)
                               && lastGetValue > 0) {
                        // we're iterating backward in order, short circuit for
                        // performance reasons (RT-39776)
                        lastGetIndex--;
                        lastGetValue = bitset.previousSetBit(lastGetValue - 1);
                        return lastGetValue;
                    } else {
                        for (lastGetIndex = 0, lastGetValue = bitset.nextSetBit(0); lastGetValue >= 0
                                                                                    || lastGetIndex == index; lastGetIndex++, lastGetValue = bitset.nextSetBit(lastGetValue
                                                                                                                                                               + 1)) {
                            if (lastGetIndex == index) {
                                return lastGetValue;
                            }
                        }
                    }

                    return -1;
                }

                @Override
                public int size() {
                    return bitset.cardinality();
                }
            };
        }

        private void quietClearSelection() {
            selectedIndices.clear();
        }
    }

    static class ShiftParams {
        private final int     clearIndex;
        private final boolean selected;
        private final int     setIndex;

        ShiftParams(int clearIndex, int setIndex, boolean selected) {
            this.clearIndex = clearIndex;
            this.setIndex = setIndex;
            this.selected = selected;
        }

        public final int getClearIndex() {
            return clearIndex;
        }

        public final int getSetIndex() {
            return setIndex;
        }

        public final boolean isSelected() {
            return selected;
        }
    }

    private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

    static {
        List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Region.getClassCssMetaData());
        STYLEABLES = Collections.unmodifiableList(styleables);
    }

    /**
     * Creates a viewport that lays out content vertically from top to bottom
     */
    public static <T, C extends Cell<T, ?>> VirtualFlow<T, C> createVertical(double cellBreadth,
                                                                             double cellLength,
                                                                             ObservableList<T> items,
                                                                             Function<? super T, ? extends C> cellFactory) {
        return createVertical(cellBreadth, cellLength, items, cellFactory,
                              Gravity.FRONT);
    }

    /**
     * Creates a viewport that lays out content vertically from top to bottom
     */
    public static <T, C extends Cell<T, ?>> VirtualFlow<T, C> createVertical(double cellBreadth,
                                                                             double cellLength,
                                                                             ObservableList<T> items,
                                                                             Function<? super T, ? extends C> cellFactory,
                                                                             Gravity gravity) {
        return new VirtualFlow<>(cellBreadth, cellLength, items, cellFactory,
                                 gravity);
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return STYLEABLES;
    }

    static <T> ListChangeListener.Change<T> buildClearAndSelectChange(ObservableList<T> list,
                                                                      List<T> removed,
                                                                      int retainedRow) {
        return new ListChangeListener.Change<T>(list) {
            private boolean       atFirstRange = true;

            private final int[]   EMPTY_PERM   = new int[0];

            private final List<T> firstRemovedRange;
            private int           from         = -1;

            private boolean       invalid      = true;
            private final int     removedSize  = removed.size();

            private final List<T> secondRemovedRange;

            {
                int midIndex = retainedRow >= removedSize ? removedSize
                                                          : retainedRow < 0 ? 0
                                                                            : retainedRow;
                firstRemovedRange = removed.subList(0, midIndex);
                secondRemovedRange = removed.subList(midIndex, removedSize);
            }

            @Override
            public int getFrom() {
                checkState();
                return from;
            }

            @Override
            public List<T> getRemoved() {
                checkState();
                return atFirstRange ? firstRemovedRange : secondRemovedRange;
            }

            @Override
            public int getRemovedSize() {
                return atFirstRange ? firstRemovedRange.size()
                                    : secondRemovedRange.size();
            }

            @Override
            public int getTo() {
                return getFrom();
            }

            @Override
            public boolean next() {
                if (invalid && atFirstRange) {
                    invalid = false;

                    // point 'from' to the first position, relative to
                    // the underlying selectedCells index.
                    from = 0;
                    return true;
                }

                if (atFirstRange && !secondRemovedRange.isEmpty()) {
                    atFirstRange = false;

                    // point 'from' to the second position, relative to
                    // the underlying selectedCells index.
                    from = 1;
                    return true;
                }

                return false;
            }

            @Override
            public void reset() {
                invalid = true;
                atFirstRange = true;
            }

            @Override
            protected int[] getPermutation() {
                checkState();
                return EMPTY_PERM;
            }

            private void checkState() {
                if (invalid) {
                    throw new IllegalStateException("Invalid Change state: next() must be called before inspecting the Change.");
                }
            }
        };
    }

    protected final ObservableList<T>       items;

    protected final MouseHandler            mouseHandler;

    private final Var<Double>               breadthOffset;

    // non-negative
    private final Var<Double>               breadthOffset0 = Var.newSimpleVar(0.0);
    private final CellListManager<T, C>     cellListManager;

    private final CellPositioner<T, C>      cellPositioner;

    private final Var<Double>               lengthOffsetEstimate;

    private final Navigator<T, C>           navigator;

    private final VirtualFlowSelectionModel selectionModel = new VirtualFlowSelectionModel();

    private final SizeTracker               sizeTracker;

    private final FocusTraversal            focus;

    {

        focus = new FocusTraversal() {

            @Override
            protected Node getNode() {
                return VirtualFlow.this;
            }

            @Override
            public void activate() {
                int focusedIndex = selectionModel.getFocusedIndex();
                selectionModel.select(focusedIndex);
                if (focusedIndex >= 0) {
                    edit();
                }
            }

            private void edit() {
            }
        };
        mouseHandler = new MouseHandler() {

            @Override
            public Node getNode() {
                return VirtualFlow.this;
            }

            public void select(MouseEvent evt) {
                VirtualFlowHit<C> hit = hit(evt.getX(), evt.getY());
                if (hit.isCellHit()) {
                    hit.getCell()
                       .setFocus(true);
                }
            }
        };

    }

    protected VirtualFlow(double cellBreadth, double cellLength,
                          ObservableList<T> items,
                          Function<? super T, ? extends C> cellFactory,
                          Gravity gravity) {
        breadthOffset = breadthOffset0.asVar(this::setBreadthOffset);
        this.getStyleClass()
            .add("virtual-flow");
        this.items = items;
        this.cellListManager = new CellListManager<T, C>(items, cellFactory);
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

        // scroll content by mouse scroll
        this.addEventHandler(ScrollEvent.SCROLL, se -> {
            scrollXBy(-se.getDeltaX());
            scrollYBy(-se.getDeltaY());
            se.consume();
        });
    }

    public Var<Double> breadthOffsetProperty() {
        return breadthOffset;
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
        navigator.dispose();
        sizeTracker.dispose();
        cellListManager.dispose();
    }

    @Override
    public Var<Double> estimatedScrollXProperty() {
        return this.breadthOffsetProperty();
    }

    @Override
    public Var<Double> estimatedScrollYProperty() {
        return this.lengthOffsetEstimateProperty();
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

    public ObservableList<T> getItems() {
        return items;
    }

    public VirtualFlowSelectionModel getSelectionModel() {
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
    public VirtualFlowHit<C> hit(double x, double y) {
        double bOff = x;
        double lOff = y;

        bOff += breadthOffset0.getValue();

        if (items.isEmpty()) {
            return VirtualFlowHit.hitAfterCells(bOff, lOff);
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
            return VirtualFlowHit.hitBeforeCells(bOff,
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
                return VirtualFlowHit.hitAfterCells(bOff,
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
                        return VirtualFlowHit.cellHit(i, cell, bOff,
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
     * Scroll the content horizontally by the given amount.
     *
     * @param deltaX
     *            positive value scrolls right, negative value scrolls left
     */
    @Override
    public void scrollXBy(double deltaX) {
        this.scrollBreadth(deltaX);
    }

    /**
     * Scroll the content horizontally to the pixel
     *
     * @param pixel
     *            - the pixel position to which to scroll
     */
    @Override
    public void scrollXToPixel(double pixel) {
        this.setBreadthOffset(pixel);
    }

    /**
     * Scroll the content vertically by the given amount.
     *
     * @param deltaY
     *            positive value scrolls down, negative value scrolls up
     */
    @Override
    public void scrollYBy(double deltaY) {
        this.scrollLength(deltaY);
    }

    /**
     * Scroll the content vertically to the pixel
     *
     * @param pixel
     *            - the pixel position to which to scroll
     */
    @Override
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
        showBreadthRegion(region.getMinX(),
                          region.getMinX() + region.getWidth());
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

    @Override
    public Val<Double> totalHeightEstimateProperty() {
        return this.totalLengthEstimateProperty();
    }

    public Val<Double> totalLengthEstimateProperty() {
        return sizeTracker.totalLengthEstimateProperty();
    }

    @Override
    public Val<Double> totalWidthEstimateProperty() {
        return this.totalBreadthEstimateProperty();
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

        double viewBreadth = this.getLayoutBounds()
                                 .getWidth();
        double navigatorBreadth = navigator.getLayoutBounds()
                                           .getWidth();
        double totalBreadth = breadthOffset0.getValue();
        double breadthDifference = navigatorBreadth - totalBreadth;
        if (breadthDifference < viewBreadth) {
            // viewport is scrolled all the way to the end of its breadth.
            //  but now viewport size (breadth) has increased
            double adjustment = viewBreadth - breadthDifference;
            navigator.relocate(-(totalBreadth - adjustment), (double) 0);
            breadthOffset0.setValue(totalBreadth - adjustment);
        } else {
            navigator.relocate(-breadthOffset0.getValue(), (double) 0);
        }
    }

    void scrollBreadth(double deltaBreadth) {
        setBreadthOffset(breadthOffset0.getValue() + deltaBreadth);
    }

    void scrollLength(double deltaLength) {
        setLengthOffset(lengthOffsetEstimate.getValue() + deltaLength);
    }

    void setBreadthOffset(double pixels) {
        double total = totalBreadthEstimateProperty().getValue();
        double breadth = sizeTracker.getViewportBreadth();
        double max = Math.max(total - breadth, 0);
        double current = breadthOffset0.getValue();

        if (pixels > max) {
            pixels = max;
        }
        if (pixels < 0) {
            pixels = 0;
        }

        if (pixels != current) {
            breadthOffset0.setValue(pixels);
            requestLayout();
            // TODO: could be safely relocated right away?
            // (Does relocation request layout?)
        }
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
        return 100;
    }

    private double computePrefLength(double breadth) {
        return 100;
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

    private void showBreadthRegion(double fromX, double toX) {
        double bOff = breadthOffset0.getValue();
        double spaceBefore = fromX - bOff;
        double spaceAfter = sizeTracker.getViewportBreadth() - toX + bOff;
        if (spaceBefore < 0 && spaceAfter > 0) {
            double shift = Math.min(-spaceBefore, spaceAfter);
            setBreadthOffset(bOff - shift);
        } else if (spaceAfter < 0 && spaceBefore > 0) {
            double shift = Math.max(spaceAfter, -spaceBefore);
            setBreadthOffset(bOff - shift);
        }
    }

}
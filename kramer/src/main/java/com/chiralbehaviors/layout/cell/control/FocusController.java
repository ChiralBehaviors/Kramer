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

package com.chiralbehaviors.layout.cell.control;

import static javafx.scene.input.KeyCode.DOWN;
import static javafx.scene.input.KeyCode.END;
import static javafx.scene.input.KeyCode.ENTER;
import static javafx.scene.input.KeyCode.HOME;
import static javafx.scene.input.KeyCode.KP_DOWN;
import static javafx.scene.input.KeyCode.KP_LEFT;
import static javafx.scene.input.KeyCode.KP_RIGHT;
import static javafx.scene.input.KeyCode.KP_UP;
import static javafx.scene.input.KeyCode.LEFT;
import static javafx.scene.input.KeyCode.RIGHT;
import static javafx.scene.input.KeyCode.TAB;
import static javafx.scene.input.KeyCode.UP;
import static javafx.scene.input.KeyCombination.SHIFT_DOWN;
import static org.fxmisc.wellbehaved.event.EventPattern.keyPressed;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.consume;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.sequence;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.unless;

import java.util.ArrayList;
import java.util.List;

import org.fxmisc.wellbehaved.event.template.InputMapTemplate;

import com.chiralbehaviors.layout.AutoLayout;
import com.chiralbehaviors.layout.cell.Hit;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.LayoutContainer;
import com.chiralbehaviors.layout.flowless.VirtualFlow;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.InputEvent;

/**
 * @author halhildebrand
 *
 */
public class FocusController<C extends LayoutCell<?>>
        implements FocusTraversal<C> {
    private final static InputMapTemplate<FocusController<?>, InputEvent> TRAVERSAL_INPUT_MAP;

    static {
        TRAVERSAL_INPUT_MAP = unless(c -> c.isDisabled(),
                                     sequence(consume(keyPressed(TAB),
                                                      (traversal,
                                                       evt) -> traversal.traverseCurrentNext()),
                                              consume(keyPressed(TAB,
                                                                 SHIFT_DOWN),
                                                      (traversal,
                                                       evt) -> traversal.traverseCurrentPrevious()),
                                              consume(keyPressed(UP),
                                                      (traversal,
                                                       evt) -> traversal.up()),
                                              consume(keyPressed(KP_UP),
                                                      (traversal,
                                                       evt) -> traversal.up()),
                                              consume(keyPressed(DOWN),
                                                      (traversal,
                                                       evt) -> traversal.down()),
                                              consume(keyPressed(KP_DOWN),
                                                      (traversal,
                                                       evt) -> traversal.down()),
                                              consume(keyPressed(LEFT),
                                                      (traversal,
                                                       evt) -> traversal.left()),
                                              consume(keyPressed(KP_LEFT),
                                                      (traversal,
                                                       evt) -> traversal.left()),
                                              consume(keyPressed(RIGHT),
                                                      (traversal,
                                                       evt) -> traversal.right()),
                                              consume(keyPressed(KP_RIGHT),
                                                      (traversal,
                                                       evt) -> traversal.right()),
                                              consume(keyPressed(ENTER),
                                                      (traversal,
                                                       evt) -> traversal.currentActivate()),
                                              consume(keyPressed(HOME),
                                                      (traversal,
                                                       evt) -> traversal.home()),
                                              consume(keyPressed(END),
                                                      (traversal,
                                                       evt) -> traversal.end())));
    }

    private volatile FocusTraversalNode<?> current;
    private volatile CursorState           cursorState;

    public CursorState getCursorState() {
        return cursorState;
    }

    private final List<Node>               boundNodes = new ArrayList<>();
    private final Node                     node;

    public FocusController(Node node) {
        this.node = node;
    }

    @Override
    public void bindKeyboard(Node vfNode) {
        InputMapTemplate.installFallback(TRAVERSAL_INPUT_MAP, this, c -> vfNode);
        boundNodes.add(vfNode);
    }

    @Override
    public void activate() {
    }

    @Override
    public void edit() {
    }

    @Override
    public boolean isCurrent() {
        return false;
    }

    @Override
    public boolean isCurrent(FocusTraversalNode<?> node) {
        return node == current;
    }

    @Override
    public boolean propagate(SelectionEvent event) {
        return false;
    }

    @Override
    public void select(LayoutContainer<?, ?, ?> child) {
    }

    @Override
    public void selectNext() {
    }

    @Override
    public void selectNoFocus(LayoutContainer<?, ?, ?> container) {
    }

    @Override
    public void selectPrevious() {
    }

    @Override
    public void setCurrent() {
    }

    @Override
    public void setCurrent(FocusTraversalNode<?> focused) {
        current = focused;
    }

    @Override
    public void traverseNext() {
    }

    @Override
    public void traversePrevious() {
    }

    @Override
    public void resetCursorState() {
        cursorState = null;
    }

    @Override
    public void unbind() {
        for (Node n : boundNodes) {
            InputMapTemplate.uninstall(TRAVERSAL_INPUT_MAP, this, c -> n);
        }
        boundNodes.clear();
        cursorState = null;
    }

    /**
     * Recover cursor position after a layout rebuild. Called by AutoLayout
     * after autoLayout() replaces the control tree. Re-derives scene position
     * from the stored data identity by scanning the new VirtualFlow's items.
     *
     * @param saved the cursor state captured before unbind(); may be null
     * @param newFlow the new VirtualFlow to recover into
     */
    public void recoverCursor(CursorState saved, VirtualFlow<?> newFlow) {
        if (saved == null || newFlow == null) {
            return;
        }
        // Find the item by identity in the new flow
        var items = newFlow.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == saved.dataIdentity()) {
                newFlow.show(i);
                selectCellAt(newFlow, i);
                return;
            }
        }
        // Identity not found — clear cursor
        cursorState = null;
    }


    void setCursorState(CursorState state) {
        this.cursorState = state;
    }

    FocusTraversalNode<?> getCurrent() {
        return current;
    }

    protected boolean isDisabled() {
        return node.isDisabled();
    }

    void currentActivate() {
        if (current == null) return;
        current.activate();
    }

    void down() {
        if (current == null) return;
        if (cursorState != null) {
            movePhysical(0, cursorState.cellBounds().getHeight());
        } else {
            switch (current.bias) {
                case HORIZONTAL -> current.traverseNext();
                case VERTICAL -> current.selectNext();
                default -> {}
            }
        }
    }

    void left() {
        if (current == null) return;
        if (cursorState != null) {
            movePhysical(-cursorState.cellBounds().getWidth(), 0);
        } else {
            switch (current.bias) {
                case HORIZONTAL -> current.selectPrevious();
                case VERTICAL -> current.traversePrevious();
                default -> {}
            }
        }
    }

    void right() {
        if (current == null) return;
        if (cursorState != null) {
            movePhysical(cursorState.cellBounds().getWidth(), 0);
        } else {
            switch (current.bias) {
                case HORIZONTAL -> current.selectNext();
                case VERTICAL -> current.traverseNext();
                default -> {}
            }
        }
    }

    void traverseCurrentNext() {
        if (current == null) return;
        current.traverseNext();
    }

    void traverseCurrentPrevious() {
        if (current == null) return;
        current.traversePrevious();
    }

    void up() {
        if (current == null) return;
        if (cursorState != null) {
            movePhysical(0, -cursorState.cellBounds().getHeight());
        } else {
            switch (current.bias) {
                case HORIZONTAL -> current.traversePrevious();
                case VERTICAL -> current.selectPrevious();
                default -> {}
            }
        }
    }

    void home() {
        if (current == null) return;
        LayoutContainer<?, ?, ?> container = current.getContainer();
        if (container instanceof VirtualFlow<?> vf && vf.getItemCount() > 0) {
            vf.show(0);
            selectCellAt(vf, 0);
        }
    }

    void end() {
        if (current == null) return;
        LayoutContainer<?, ?, ?> container = current.getContainer();
        if (container instanceof VirtualFlow<?> vf && vf.getItemCount() > 0) {
            int lastIdx = vf.getItemCount() - 1;
            vf.show(lastIdx);
            selectCellAt(vf, lastIdx);
        }
    }

    /**
     * Physical cursor movement. Computes a new scene position from the current
     * cursor state, hit-tests against the current VirtualFlow first, then
     * falls back to root-level scene dispatch for cross-container navigation.
     * Off-screen targets use index-based advancement as final fallback.
     */
    private void movePhysical(double dx, double dy) {
        CursorState cs = cursorState;
        if (cs == null || current == null) return;

        LayoutContainer<?, ?, ?> container = current.getContainer();
        if (!(container instanceof VirtualFlow<?> vf)) return;

        double newX = cs.scenePosition().getX() + dx;
        double newY = cs.scenePosition().getY() + dy;

        // Fast path: try current VirtualFlow first
        Point2D local = vf.getNode().sceneToLocal(newX, newY);
        if (local != null) {
            Hit<?> hit = vf.hit(local.getX(), local.getY());
            if (hit.isCellHit()) {
                selectCellAt(vf, hit.getCellIndex());
                return;
            }
            // Off-screen within current VirtualFlow: index-based fallback
            if (hit.isAfterCells()) {
                vf.getLastVisibleIndex().ifPresent(lastIdx -> {
                    int nextIdx = lastIdx + 1;
                    if (nextIdx < vf.getItemCount()) {
                        vf.show(nextIdx);
                        selectCellAt(vf, nextIdx);
                    }
                });
                return;
            }
            if (hit.isBeforeCells()) {
                vf.getFirstVisibleIndex().ifPresent(firstIdx -> {
                    int prevIdx = firstIdx - 1;
                    if (prevIdx >= 0) {
                        vf.show(prevIdx);
                        selectCellAt(vf, prevIdx);
                    }
                });
                return;
            }
        }

        // Slow path: cross-container dispatch via AutoLayout root
        if (node instanceof AutoLayout autoLayout) {
            Hit<?> rootHit = autoLayout.hitSceneRoot(new Point2D(newX, newY));
            if (rootHit != null && rootHit.isCellHit()) {
                // Found a cell in a different container — update cursor position.
                // Note: current FTN is not transferred; subsequent navigation
                // still operates on the original container until focus transfer
                // is implemented.
                LayoutCell<?> hitCell = rootHit.getCell();
                Node cellNode = hitCell.getNode();
                Bounds localBounds = cellNode.getLayoutBounds();
                Point2D cellCenter = cellNode.localToScene(
                    localBounds.getWidth() / 2, localBounds.getHeight() / 2);
                cursorState = new CursorState(
                    cs.dataIdentity(), cs.fieldPath(),
                    rootHit.getCellIndex(),
                    cellCenter != null ? cellCenter : new Point2D(newX, newY),
                    localBounds);
            }
        }
    }

    /**
     * Navigate to the cell at the given index in the VirtualFlow, updating
     * both the selection model and the cursor state. Public entry point for
     * programmatic navigation.
     *
     * @param vf    the VirtualFlow to navigate within
     * @param index the zero-based cell index to select
     */
    public void navigateTo(VirtualFlow<?> vf, int index) {
        selectCellAt(vf, index);
    }

    /**
     * Select a cell at the given index in the VirtualFlow, updating both
     * the selection model and the cursor state.
     */
    private void selectCellAt(VirtualFlow<?> vf, int index) {
        if (index < 0 || index >= vf.getItemCount()) return;

        var selModel = vf.getSelectionModel();
        selModel.select(index);

        // Derive cursor state from the selected cell
        var items = vf.getItems();
        Object identity = items.get(index);

        LayoutCell<?> cell = selModel.getCell(index);
        Node cellNode = cell.getNode();
        Bounds localBounds = cellNode.getLayoutBounds();
        // Convert cell center to scene coordinates for cursor position
        Point2D cellCenter = cellNode.localToScene(
            localBounds.getWidth() / 2, localBounds.getHeight() / 2);
        if (cellCenter == null) {
            // Node not in scene graph yet — use placeholder
            cellCenter = new Point2D(0, 0);
        }

        cursorState = new CursorState(
            identity,
            null,  // fieldPath — not yet implemented; requires schema-aware lookup
            index,
            cellCenter,
            localBounds
        );
    }
}

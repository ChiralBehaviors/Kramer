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
import static javafx.scene.input.KeyCode.ENTER;
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

import org.fxmisc.wellbehaved.event.template.InputMapTemplate;

import com.chiralbehaviors.layout.cell.LayoutCell;

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
                                                       evt) -> traversal.activate())));
    }

    private volatile FocusTraversalNode<?> current;

    private final Node                     node;

    public FocusController(Node node) {
        this.node = node;
        InputMapTemplate.installFallback(TRAVERSAL_INPUT_MAP, this, c -> node);
    }

    @Override
    public void activate() {
    }

    @Override
    public void edit() {
    }

    @Override
    public void selectNext() {
    }

    @Override
    public void selectPrevious() {
    }

    public void setCurrent(FocusTraversalNode<?> focused) {
        current = focused;
    }

    @Override
    public void traverseNext() {
    }

    @Override
    public void traversePrevious() {
    }

    public void unbind() {
        InputMapTemplate.uninstall(TRAVERSAL_INPUT_MAP, this, c -> node);
    }

    protected boolean isDisabled() {
        return node.isDisabled();
    }

    private void down() {
        switch (current.bias) {
            case HORIZONTAL:
                current.traverseNext();
                break;
            case VERTICAL:
                current.selectNext();
                break;
            default:
                break;
        }
    }

    private void left() {
        switch (current.bias) {
            case HORIZONTAL:
                current.selectPrevious();
                break;
            case VERTICAL:
                current.traversePrevious();
                break;
            default:
                break;
        }
    }

    private void right() {
        switch (current.bias) {
            case HORIZONTAL:
                current.selectNext();
                break;
            case VERTICAL:
                current.traverseNext();
                break;
            default:
                break;
        }
    }

    private void traverseCurrentNext() {
        current.traverseNext();
    }

    private void traverseCurrentPrevious() {
        current.traversePrevious();
    }

    private void up() {
        switch (current.bias) {
            case HORIZONTAL:
                current.traversePrevious();
                break;
            case VERTICAL:
                current.selectPrevious();
                break;
            default:
                break;
        }
    }
}

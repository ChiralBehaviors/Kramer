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

package com.chiralbehaviors.layout.cell;

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

import javafx.scene.Node;
import javafx.scene.input.InputEvent;

/**
 * @author halhildebrand
 *
 */
abstract public class FocusTraversal {

    public static enum Bias {
        HORIZONTAL,
        VERTICAL;
    }

    private final static InputMapTemplate<FocusTraversal, InputEvent> TRAVERSAL_INPUT_MAP;

    static {
        TRAVERSAL_INPUT_MAP = unless(c -> c.isDisabled(),
                                     sequence(consume(keyPressed(TAB),
                                                      (traversal,
                                                       evt) -> traversal.traverseNext()),
                                              consume(keyPressed(TAB,
                                                                 SHIFT_DOWN),
                                                      (traversal,
                                                       evt) -> traversal.traversePrevious()),
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

    private final Bias           bias;
    private final FocusTraversal parent;

    public FocusTraversal(FocusTraversal parent, Bias bias) {
        this.bias = bias;
        this.parent = parent;
        bind();
    }

    public void activate() {

    }

    public void bind() {
        InputMapTemplate.installFallback(TRAVERSAL_INPUT_MAP, this,
                                         c -> getNode());
    }

    public void selectNext() {
    }

    public void selectPrevious() {

    }

    public final void traverseNext() {
        if (parent == null) {
            System.out.println(String.format("traverse next: %s, null parent",
                                             this.getNode()
                                                 .getClass()
                                                 .getSimpleName()));
            return;
        }
        System.out.println(String.format("traverse next: %s, parent: %s",
                                         this.getNode()
                                             .getClass()
                                             .getSimpleName(),
                                         parent.getNode()
                                               .getClass()
                                               .getSimpleName()));
        parent.selectNext();
    }

    public final void traversePrevious() {
        if (parent == null) {
            System.out.println(String.format("traverse previous: %s, null parent",
                                             this.getNode()
                                                 .getClass()
                                                 .getSimpleName()));
            return;
        }
        System.out.println(String.format("traverse previous: %s, parent: %s",
                                         this.getNode()
                                             .getClass()
                                             .getSimpleName(),
                                         parent.getNode()
                                               .getClass()
                                               .getSimpleName()));
        parent.selectPrevious();
    }

    public void unbind() {
        InputMapTemplate.uninstall(TRAVERSAL_INPUT_MAP, this, c -> getNode());
    }

    abstract protected Node getNode();

    private void down() {
        switch (bias) {
            case HORIZONTAL:
                traverseNext();
                break;
            case VERTICAL:
                selectNext();
                break;
            default:
                break;
        }
    }

    private boolean isDisabled() {
        return getNode().isDisabled();
    }

    private void left() {
        switch (bias) {
            case HORIZONTAL:
                selectPrevious();
                break;
            case VERTICAL:
                traversePrevious();
                break;
            default:
                break;
        }
    }

    private void right() {
        switch (bias) {
            case HORIZONTAL:
                selectNext();
                break;
            case VERTICAL:
                traverseNext();
                break;
            default:
                break;
        }
    }

    private void up() {
        switch (bias) {
            case HORIZONTAL:
                traversePrevious();
                break;
            case VERTICAL:
                selectPrevious();
                break;
            default:
                break;
        }
    }

}
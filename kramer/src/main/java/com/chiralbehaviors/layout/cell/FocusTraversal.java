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
                                                       evt) -> traversal.selectPrevious()),
                                              consume(keyPressed(KP_UP),
                                                      (traversal,
                                                       evt) -> traversal.selectPrevious()),
                                              consume(keyPressed(DOWN),
                                                      (traversal,
                                                       evt) -> traversal.selectNext()),
                                              consume(keyPressed(KP_DOWN),
                                                      (traversal,
                                                       evt) -> traversal.selectNext()),
                                              consume(keyPressed(LEFT),
                                                      (traversal,
                                                       evt) -> traversal.traverseLeft()),
                                              consume(keyPressed(KP_LEFT),
                                                      (traversal,
                                                       evt) -> traversal.traverseLeft()),
                                              consume(keyPressed(RIGHT),
                                                      (traversal,
                                                       evt) -> traversal.traverseRight()),
                                              consume(keyPressed(KP_RIGHT),
                                                      (traversal,
                                                       evt) -> traversal.traverseRight()),
                                              consume(keyPressed(ENTER),
                                                      (traversal,
                                                       evt) -> traversal.activate())));
    }

    private boolean isDisabled() {
        return false;
    }

    public void activate() {

    }

    public void selectNext() {

    }

    public void selectPrevious() {

    }

    public void traverseLeft() {

    }

    public void traverseNext() {

    }

    public void traversePrevious() {

    }

    public void traverseRight() {

    }

    protected void bind(Node node) {
        InputMapTemplate.installFallback(TRAVERSAL_INPUT_MAP, this, c -> node);
    }

    protected void unbind(Node node) {
        InputMapTemplate.uninstall(TRAVERSAL_INPUT_MAP, this, c -> node);
    }

}
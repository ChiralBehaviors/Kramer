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

import javafx.scene.input.InputEvent;

/**
 * @author halhildebrand
 *
 */
public class FocusTraversal {

    private final InputMapTemplate<LayoutCell<?>, InputEvent> template;

    {
        template = unless(c -> c.getNode()
                                .isDisabled(),
                          sequence(consume(keyPressed(TAB),
                                           (cell, evt) -> traverseNext(cell)),
                                   consume(keyPressed(TAB, SHIFT_DOWN),
                                           (cell,
                                            evt) -> traversePrevious(cell)),
                                   consume(keyPressed(UP),
                                           (cell, evt) -> selectPrevious(cell)),
                                   consume(keyPressed(KP_UP),
                                           (cell, evt) -> selectPrevious(cell)),
                                   consume(keyPressed(DOWN),
                                           (cell, evt) -> selectNext(cell)),
                                   consume(keyPressed(KP_DOWN),
                                           (cell, evt) -> selectNext(cell)),
                                   consume(keyPressed(LEFT),
                                           (cell, evt) -> traverseLeft(cell)),
                                   consume(keyPressed(KP_LEFT),
                                           (cell, evt) -> traverseLeft(cell)),
                                   consume(keyPressed(RIGHT),
                                           (cell, evt) -> traverseRight(cell)),
                                   consume(keyPressed(KP_RIGHT),
                                           (cell, evt) -> traverseRight(cell)),
                                   consume(keyPressed(ENTER),
                                           (cell, evt) -> activate(cell))));
    }

    public void activate(LayoutCell<?> cell) {

    }

    public void selectNext(LayoutCell<?> cell) {

    }

    public void selectPrevious(LayoutCell<?> cell) {

    }

    public void traverseLeft(LayoutCell<?> cell) {

    }

    public void traverseNext(LayoutCell<?> cell) {

    }

    public void traversePrevious(LayoutCell<?> cell) {

    }

    public void traverseRight(LayoutCell<?> cell) {

    }

    protected void bind(LayoutCell<?> cell) {
        InputMapTemplate.installFallback(template, cell, c -> c.getNode());
    }

    protected void unbind(LayoutCell<?> cell) {
        InputMapTemplate.uninstall(template, cell, c -> c.getNode());
    }

}
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

import static org.fxmisc.wellbehaved.event.EventPattern.mouseClicked;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.consume;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.sequence;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.unless;

import org.fxmisc.wellbehaved.event.template.InputMapTemplate;

import javafx.animation.PauseTransition;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;
import javafx.scene.input.InputEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * @author halhildebrand
 *
 */
abstract public class MouseHandler {
    private static final InputMapTemplate<MouseHandler, InputEvent> DEFAULT_INPUT_MAP;

    static {
        DEFAULT_INPUT_MAP = unless(h -> h.isDisabled(),
                                   sequence(consume(mouseClicked(MouseButton.PRIMARY),
                                                    (handler,
                                                     evt) -> handler.select(evt))));
    }

    private final PauseTransition clickTimer;
    private volatile MouseEvent   mouseEvent;
    private final IntegerProperty sequentialClickCount = new SimpleIntegerProperty(0);

    public MouseHandler(Duration maxTimeBetweenSequentialClicks) {
        bind();
        clickTimer = new PauseTransition(maxTimeBetweenSequentialClicks);
        clickTimer.setOnFinished(event -> {
            int count = sequentialClickCount.get();
            sequentialClickCount.set(0);
            switch (count) {
                case 1:
                    singleClick(mouseEvent);
                    break;
                case 2:
                    doubleClick(mouseEvent);
                    break;
                case 3:
                    tripleClick(mouseEvent);
                    break;
                default:
            }
        });

    }

    public void bind() {
        InputMapTemplate.installOverride(DEFAULT_INPUT_MAP, this,
                                         c -> getNode());
    }

    public void doubleClick(MouseEvent mouseEvent) {
    }

    abstract public Node getNode();

    public boolean isDisabled() {
        return false;
    }

    public void singleClick(MouseEvent mouseEvent) {
    }

    public void tripleClick(MouseEvent mouseEvent) {
    }

    public void unbind() {
        InputMapTemplate.uninstall(DEFAULT_INPUT_MAP, this, c -> getNode());
    }

    private void select(MouseEvent evt) {
        mouseEvent = evt;
        sequentialClickCount.set(sequentialClickCount.get() + 1);
        clickTimer.playFromStart();
    }

}

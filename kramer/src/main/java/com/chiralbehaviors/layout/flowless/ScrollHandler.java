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

package com.chiralbehaviors.layout.flowless;

import static javafx.scene.input.KeyCode.PAGE_DOWN;
import static javafx.scene.input.KeyCode.PAGE_UP;
import static org.fxmisc.wellbehaved.event.EventPattern.keyPressed;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.consume;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.sequence;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.unless;

import org.fxmisc.wellbehaved.event.template.InputMapTemplate;

import javafx.scene.input.InputEvent;
import javafx.scene.input.ScrollEvent;

/**
 * @author halhildebrand
 *
 */
public class ScrollHandler {
    private static final InputMapTemplate<ScrollHandler, InputEvent> DEFAULT_INPUT_MAP;
    static {
        DEFAULT_INPUT_MAP = unless(h -> h.isDisabled(),
                                   sequence(consume(ScrollEvent.SCROLL,
                                                    (handler,
                                                     evt) -> handler.scroll(evt)),
                                            consume(keyPressed(PAGE_UP),
                                                    (handler,
                                                     evt) -> handler.scrollUp()),
                                            consume(keyPressed(PAGE_DOWN),
                                                    (handler,
                                                     evt) -> handler.scrollDown())));
    }

    private VirtualFlow<?, ?> flow;

    public ScrollHandler(VirtualFlow<?, ?> flow) {
        assert flow != null;
        this.flow = flow;
        bind();
    }

    public void scroll(ScrollEvent se) { 
        flow.scrollYBy(-se.getDeltaY());
    }

    public void bind() {
        InputMapTemplate.installOverride(DEFAULT_INPUT_MAP, this, c -> flow);
    }

    public boolean isDisabled() {
        return flow.isDisabled();
    }

    public void scrollDown() {
        flow.scrollDown();
    }

    public void scrollUp() {
        flow.scrollUp();
    }

    public void unbind() {
        InputMapTemplate.uninstall(DEFAULT_INPUT_MAP, this, c -> flow);
    }

}

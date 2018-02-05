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

import com.chiralbehaviors.layout.flowless.Cell;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * @author halhildebrand
 *
 */
public class SelectionEvent extends Event {
    public static final EventType<SelectionEvent> ALL              = new EventType<SelectionEvent>("SELECT");
    public static final EventType<SelectionEvent> DOUBLE_SELECT;
    public static final EventType<SelectionEvent> SINGLE_SELECT;
    public static final EventType<SelectionEvent> TRIPLE_SELECT;

    private static final long                     serialVersionUID = 1L;
    static {
        SINGLE_SELECT = new EventType<>(ALL, "SINGLE_SELECT");
        DOUBLE_SELECT = new EventType<>(SINGLE_SELECT, "DOUBLE_SELECT");
        TRIPLE_SELECT = new EventType<>(DOUBLE_SELECT, "TRIPLE_SELECT");
    }

    private final boolean    composed;
    private final Cell<?, ?> selected;

    public SelectionEvent(Cell<?, ?> selected,
                          EventType<? extends Event> eventType) {
        this(selected, eventType, false);

    }

    public SelectionEvent(Cell<?, ?> selected,
                          EventType<? extends Event> eventType,
                          boolean composed) {
        super(null, null, eventType);
        this.selected = selected;
        this.composed = composed;
    }

    public Cell<?, ?> getSelected() {
        return selected;
    }

    public boolean isComposed() {
        return composed;
    }
}

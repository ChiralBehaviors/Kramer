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

package com.chiralbehaviors.layout.table;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.cell.HorizontalCell;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Point2D;
import javafx.scene.layout.Region;

/**
 * @author halhildebrand
 *
 */
public class NestedCell extends HorizontalCell<NestedCell> {
    private static final String            DEFAULT_STYLE = "nested-cell";
    private static final String            STYLE_SHEET   = "nested-cell.css";
    private final List<Consumer<JsonNode>> consumers     = new ArrayList<>();

    public NestedCell() {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
    }

    public NestedCell(double rendered, RelationLayout layout) {
        this();
        getStyleClass().add(layout.getField());
        Point2D expanded = expand(layout.getJustifiedWidth(), rendered);
        setMinSize(expanded.getX(), expanded.getY());
        setPrefSize(expanded.getX(), expanded.getY());
        setMaxSize(expanded.getX(), expanded.getY());
        layout.forEach(child -> {
            LayoutCell<? extends Region> cell = child.buildColumn(rendered);
            consumers.add(item -> cell.updateItem(child.extractFrom(item)));
            Region control = cell.getNode();
            getChildren().add(control);
        });
    }

    public void setFocus(boolean focus) {
        super.setFocused(focus);
    }

    @Override
    public void updateIndex(int index) {
        boolean active = ((index % 2) == 0);
        pseudoClassStateChanged(PSEUDO_CLASS_EVEN, active);
        pseudoClassStateChanged(PSEUDO_CLASS_ODD, !active);
    }

    @Override
    public void updateItem(JsonNode item) {
        consumers.forEach(c -> c.accept(item));
    }
}

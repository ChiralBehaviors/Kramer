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

import com.chiralbehaviors.layout.LayoutCell;
import com.chiralbehaviors.layout.RelationLayout;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * @author halhildebrand
 *
 */
public class NestedCell extends HBox implements LayoutCell<NestedCell> {
    private static final String            DEFAULT_STYLE = "nested-cell";
    private final List<Consumer<JsonNode>> consumers     = new ArrayList<>();;

    public NestedCell(double rendered, RelationLayout layout) {
        setDefaultStyles(DEFAULT_STYLE);
        getStyleClass().add(layout.getField());
        setMinSize(layout.getJustifiedWidth(), rendered);
        setPrefSize(layout.getJustifiedWidth(), rendered);
        setMaxSize(layout.getJustifiedWidth(), rendered);
        layout.forEach(child -> {
            LayoutCell<? extends Region> cell = child.buildColumn(rendered);
            consumers.add(item -> cell.updateItem(child.extractFrom(item)));
            Region control = cell.getNode();
            getChildren().add(control);
        });
    }

    @Override
    public void updateItem(JsonNode item) {
        consumers.forEach(c -> c.accept(item));
    }
}

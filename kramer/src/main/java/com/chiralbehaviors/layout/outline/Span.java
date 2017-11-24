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

package com.chiralbehaviors.layout.outline;

import java.util.ArrayList;
import java.util.List;

import com.chiralbehaviors.layout.Column;
import com.chiralbehaviors.layout.LayoutCell;
import com.chiralbehaviors.layout.flowless.Cell;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.layout.HBox;

/**
 * @author halhildebrand
 *
 */
public class Span extends HBox implements LayoutCell<Span> {

    private static final String                       DEFAULT_STYLE = "span";
    private final List<Cell<JsonNode, OutlineColumn>> columns       = new ArrayList<>();

    public Span(double justified, List<Column> columns, int cardinality,
                double cellHeight, double labelWidth) {
        setDefaultStyles(DEFAULT_STYLE);
        setMinSize(justified, cellHeight);
        setPrefSize(justified, cellHeight);
        setMaxSize(justified, cellHeight);

        columns.forEach(c -> {
            Cell<JsonNode, OutlineColumn> cell = new OutlineColumn(c,
                                                                   cardinality,
                                                                   labelWidth,
                                                                   cellHeight);
            this.columns.add(cell);
            getChildren().add(cell.getNode());
        });
    }

    @Override
    public void updateItem(JsonNode item) {
        columns.forEach(c -> c.updateItem(item));
    }
}

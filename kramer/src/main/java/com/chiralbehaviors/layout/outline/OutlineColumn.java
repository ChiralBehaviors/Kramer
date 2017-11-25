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
import java.util.function.Consumer;

import com.chiralbehaviors.layout.Column;
import com.chiralbehaviors.layout.cell.VerticalCell;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author halhildebrand
 *
 */
public class OutlineColumn extends VerticalCell<OutlineColumn> {

    private static final String      DEFAULT_STYLE = "span";
    private static final String      STYLE_SHEET   = "outline-column.css";
    private List<Consumer<JsonNode>> fields;

    public OutlineColumn(Column c, int cardinality, double labelWidth,
                         double cellHeight) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        setMinSize(c.getWidth(), cellHeight);
        setMaxSize(c.getWidth(), cellHeight);
        setPrefSize(c.getWidth(), cellHeight);
        fields = new ArrayList<>();
        c.getFields()
         .forEach(field -> {
             OutlineElement cell = field.outlineElement(cardinality, labelWidth,
                                                        c.getWidth());
             fields.add(item -> cell.updateItem(field.extractFrom(item)));
             getChildren().add(cell.getNode());
         });
    }

    @Override
    public void updateItem(JsonNode item) {
        fields.forEach(m -> m.accept(item));
    }
}

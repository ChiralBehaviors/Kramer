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

import java.util.Collection;
import java.util.List;

import com.chiralbehaviors.layout.ColumnSet;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.collections.FXCollections;

/**
 * @author halhildebrand
 *
 */
public class Outline extends VirtualFlow<JsonNode, OutlineCell> {
    private static final String DEFAULT_STYLE         = "outline";
    private static final String SCHEMA_CLASS_TEMPLATE = "%s-outline";
    private static final String STYLE_SHEET           = "outline.css";

    public Outline(double height, Collection<ColumnSet> columnSets,
                   int averageCardinality, RelationLayout layout) {
        this(layout.getJustifiedWidth(),
             layout.outlineCellHeight(columnSets.stream()
                                                .mapToDouble(cs -> cs.getCellHeight())
                                                .sum()),
             columnSets, averageCardinality, layout);
    }

    public Outline(String field) {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
        getStyleClass().add(String.format(SCHEMA_CLASS_TEMPLATE, field));
    }

    public Outline(double width, double cellHeight,
                   Collection<ColumnSet> columnSets, int averageCardinality,
                   RelationLayout layout) {
        super(STYLE_SHEET, width, cellHeight,
              FXCollections.observableArrayList(), item -> {
                  OutlineCell outlineCell = new OutlineCell(columnSets,
                                                            averageCardinality,
                                                            layout.baseOutlineCellHeight(cellHeight),
                                                            layout);
                  outlineCell.updateItem(item);
                  return outlineCell;
              });
        layout.apply(this);
    }

    @Override
    public void dispose() {
        focus.unbind();
        mouseHandler.unbind();
        if (scrollHandler != null) {
            scrollHandler.unbind();
        }
    }

    @Override
    public void updateItem(JsonNode item) {
        List<JsonNode> list = SchemaNode.asList(item);
        items.setAll(list);
    }
}

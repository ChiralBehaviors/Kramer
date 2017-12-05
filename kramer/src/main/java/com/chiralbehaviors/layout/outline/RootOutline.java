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
import java.util.function.Function;

import com.chiralbehaviors.layout.ColumnSet;
import com.chiralbehaviors.layout.RelationLayout;
import com.fasterxml.jackson.databind.JsonNode; 

/**
 * @author halhildebrand
 *
 */
public class RootOutline extends Outline {

    private final Function<JsonNode, JsonNode> extractor;

    public RootOutline(double height, Collection<ColumnSet> columnSets,
                       int averageCardinality, RelationLayout layout) {
        super(height, columnSets, averageCardinality, layout);
        extractor = node -> layout.extractFrom(node);
    }

    public RootOutline(double width, double cellHeight,
                       Collection<ColumnSet> columnSets, int averageCardinality,
                       RelationLayout layout) {
        super(width, cellHeight, columnSets, averageCardinality, layout);
        extractor = node -> layout.extractFrom(node);
    }

    public RootOutline(String field) {
        super(field);
        extractor = node -> node;
    }

    @Override
    public void updateItem(JsonNode item) {
        super.updateItem(extractor.apply(item));
    }
}

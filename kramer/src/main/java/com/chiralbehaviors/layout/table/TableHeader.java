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

import java.util.List;

import com.chiralbehaviors.layout.LayoutCell;
import com.chiralbehaviors.layout.schema.SchemaNode;

import javafx.scene.layout.HBox;

/**
 * @author halhildebrand
 *
 */
public class TableHeader extends HBox implements LayoutCell<TableHeader> {

    private static final String DEFAULT_STYLE = "table-header";

    public TableHeader(double width, double height, List<SchemaNode> children) {
        setDefaultStyles(DEFAULT_STYLE);
        setMinWidth(width);
        setPrefWidth(width);
        setMaxWidth(width);
        children.forEach(c -> getChildren().add(c.buildColumnHeader()
                                                 .apply(height)));
    }
}

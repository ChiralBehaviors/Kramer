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

import com.chiralbehaviors.layout.SchemaNodeLayout;
import com.chiralbehaviors.layout.cell.HorizontalCell;

import javafx.geometry.Pos;

/**
 * @author halhildebrand
 *
 */
public class TableHeader extends HorizontalCell<TableHeader> {

    private static final String DEFAULT_STYLE = "table-header";
    private static final String STYLE_SHEET   = "table-header.css";

    public TableHeader() {
        super(STYLE_SHEET);
        setAlignment(Pos.CENTER);
        initialize(DEFAULT_STYLE);
    }

    public TableHeader(double width, double height,
                       List<SchemaNodeLayout> children) {
        this();
        setAlignment(Pos.CENTER);
        children.forEach(c -> getChildren().add(c.columnHeader()
                                                 .apply(height)));
    }
}

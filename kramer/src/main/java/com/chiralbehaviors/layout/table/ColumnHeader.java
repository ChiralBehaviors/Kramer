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

import static com.chiralbehaviors.layout.DefaultStyleProvider.snap;

import java.util.List;
import java.util.function.Function;

import com.chiralbehaviors.layout.PrimitiveLayout;
import com.chiralbehaviors.layout.RelationLayout;
import com.chiralbehaviors.layout.cell.VerticalCell;

import javafx.scene.layout.HBox;

/**
 * @author halhildebrand
 *
 */
public class ColumnHeader extends VerticalCell<ColumnHeader> {
    private static final String DEFAULT_STYLE = "column-header";
    private static final String STYLE_SHEET   = "column-header.css";

    public ColumnHeader() {
        super(STYLE_SHEET);
        initialize(DEFAULT_STYLE);
    }

    public ColumnHeader(double width, double height, PrimitiveLayout layout) {
        this();
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        getChildren().add(layout.label(width, height));
    }

    public ColumnHeader(double width, double height, RelationLayout layout,
                        List<Function<Double, ColumnHeader>> nestedHeaders) {
        this();
        HBox nested = new HBox();
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        double half = snap(height / 2.0);
        getChildren().addAll(layout.label(width, half), nested);

        nestedHeaders.forEach(n -> {
            nested.getChildren()
                  .add(n.apply(half));
        });
    }
}
